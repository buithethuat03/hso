# Server game "Hiệp Sĩ Ong Đất Online" (HSO_Re_2)

Java 17 · Maven · MySQL + HikariCP · main class `core.Start` · cổng 19129.
89 file / ~31k dòng trong `src/main/java`. Không có framework, không DI — chuỗi gọi tuần tự.

**Đọc [SERVER_REPORT.html](SERVER_REPORT.html) trước khi sửa bất cứ gì.** Đó là khảo sát đầy đủ
kèm `file:line` cho từng phát hiện: kiến trúc, bảng 55 opcode, lược đồ DB, danh sách lỗi theo
mức ưu tiên, và runbook vận hành.

Đây là server **cùng game nhưng khác version** với client đã mod ở `../client/jar/`. Hai phần
độc lập — đừng dùng `../client/jar/PROTOCOL.md` làm căn cứ cho server này.

## Build

```powershell
mvn package
```

Chạy **từ thư mục `server/`**. Ra `target/HSO_Re_2-1.0-jar-with-dependencies.jar` (bản không có
`-with-dependencies` thiếu thư viện, không chạy được).

Nếu lỗi `Unable to load the mojo 'test'` thì cache Maven hỏng, không phải lỗi source — xoá
`~/.m2/repository/org/apache/maven/plugins/maven-surefire-plugin` và
`~/.m2/repository/org/apache/maven/surefire` rồi build lại.

## Chạy

```powershell
java -jar -server target/HSO_Re_2-1.0-jar-with-dependencies.jar
```

**Thư mục làm việc bắt buộc là `server/`** — `hso.conf`, `data/`, `log/` đều là đường dẫn tương
đối. Sai chỗ là `Config file not found!` rồi `System.exit(0)`.

Cần MySQL đang chạy với database theo `hso.conf` (mặc định `hsoodtest`, import từ `hsoodtest.sql`).

## Tắt server — đọc kỹ

`Ctrl+C` / `kill` / `systemctl stop` **KHÔNG lưu dữ liệu**. Shutdown hook chỉ đóng pool DB
(`core/Start.java:5-12`), nên mất tới ~60 giây tiến trình chơi của toàn bộ người online.

Đường tắt an toàn là gửi packet `cmd -1` từ localhost (`io/Session.java:179-204`) — nó gọi
`SaveData.process()` rồi mới dừng:

```powershell
$c = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 19129)
$s = $c.GetStream(); $s.Write([byte[]]@(0xFF,0x00,0x00), 0, 3); $s.Flush()
Start-Sleep -Seconds 2; $c.Close()
```

## Bốn lỗi P0 chưa vá

Đã đọc xác nhận tận dòng. Chi tiết và cách vá ở mục 7 của báo cáo.

| Lỗi | Vị trí |
|---|---|
| SQL injection bỏ qua đăng nhập — `&&` đoản mạch khi user chứa `knightauto_hod_` | `io/Session.java:375` → `:417` |
| Rò rỉ connection cạn pool 10 — `catch` chỉ `return`, không đóng connection | `core/SaveData.java:37` · `:579-583` |
| Chống teleport vô hiệu — thân `if` rỗng vì bị comment | `map/MapService.java:141-147` |
| Mất đồ khi trade nếu túi người nhận đầy | `core/GameSrc.java:598-610` |

Kèm theo: mật khẩu lưu **dạng thô**, và `process_msg` chỉ bắt `IOException`
(`io/Session.java:209-213`) nên mọi RuntimeException giết thread người chơi mà không log gì.

## Cảnh báo về `hsoodtest.sql`

File dump này chứa **dữ liệu thật của người chơi server cũ** — mật khẩu thô, email, số điện
thoại, IP trong bảng `account`, và **mã thẻ nạp thật** trong `naptien`. Không đưa lên nơi công
khai. Phải làm sạch trước khi go-live: giữ các bảng template, xoá dữ liệu trong `account`,
`player`, `clan`, `naptien`, `giftcode`, `wedding`.

## Những chỗ dễ vấp khi sửa code

- **Đừng tin số client gửi lên.** Rất nhiều opcode dùng thẳng chỉ số từ packet để truy cập mảng
  mà không kiểm tra biên (mục 7.5 của báo cáo). Thêm code mới thì phải tự kiểm tra.
- **Không có `TODO`/`FIXME` nào trong repo.** Phần dở dang được đánh dấu bằng cách **comment cả
  khối code** — tìm code bị comment, đừng tìm thẻ TODO.
- **Túi đồ lưu dạng text tuần tự hoá** trong các cột của bảng `player`. Đổi cấu trúc vật phẩm là
  phải viết script migration cho dữ liệu người chơi hiện có.
- **760 thread bản đồ** — một thread cho mỗi khu vực của mỗi map (`core/Manager.java:138-142`),
  chạy cả khi khu đó trống. Cộng 2 thread mỗi kết nối.
- **`map.players` là `ArrayList` không thread-safe**, và việc khoá không đồng đều — chỉ 5 chỗ
  `synchronized(map)` trong `MapService.java` (2739 dòng).
- **Ghi file phải khai charset.** `core/Log.java:58` dùng `new FileWriter` không charset nên log
  tiếng Việt phụ thuộc locale OS. File đó đã import `StandardCharsets` mà không dùng.
- **Số ma thuật khắp nơi** — id map (48, 50, 53-60), id skill, id NPC đều là số trần.

## Kiểm thử

Không có test nào (`src/test` rỗng). Thay đổi luồng tiền/vật phẩm chỉ kiểm chứng được bằng chạy
tay — cẩn thận tương ứng.
