# Mod client J2ME — HSOSKY và KnightOnline

Hai game riêng, cùng một cách làm: dùng ASM patch bytecode của jar game, nhúng thêm một
"support class" viết bằng Java thường vào trong jar.

**Mọi lệnh trong tài liệu này phải chạy với thư mục hiện tại là `client/jar`.** Patcher và
`build.ps1` hardcode tên jar theo đường dẫn tương đối, chạy từ chỗ khác là không tìm thấy
file input.

Mọi `.class` trong project đều là **artifact build**, không commit, sinh lại bằng lệnh
dưới. Xoá sạch `*.class` là chuyện bình thường.

## Quy tắc chung — luôn dùng `-encoding UTF-8`

Source có comment và string tiếng Việt. Máy này mặc định `windows-1258`, thiếu cờ đó là
javac báo `unmappable character` và fail. **Mọi** lệnh `javac` trong project phải có
`-encoding UTF-8`.

Support class nhúng vào jar phải biên dịch với `--release 7` (môi trường J2ME/MIDP).
Patcher chạy trên JDK thường nên không cần.

## Build GhostMod (KnightOnline)

Cách gọn nhất, chạy đúng 3 bước và để `.class` trong `.ghostmod-build/` chứ không rải ra
thư mục gốc:

```powershell
.\build.ps1
```

Hoặc chạy tay:

```powershell
javac -encoding UTF-8 -cp "microemulator.jar;KnightOnline_402.jar" --release 7 GhostMod.java
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" GhostModPatcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" GhostModPatcher
```

Sinh ra `KnightOnline_402_Modded.jar` (input/output hardcode trong patcher, không nhận
tham số dòng lệnh). Patcher tự verify lại jar vừa ghi rồi mới publish, nên "Verify OK"
xuất hiện là jar dùng được.

**AUTOMATION RULE:** sửa `GhostMod.java` hoặc `GhostModPatcher.java` là chạy ngay 3 bước
trên để jar mod không bị lệch với source.

## Build một mod HSOSKY

Cùng khuôn cho mọi `HSOSKY*Patcher`:

```powershell
javac -encoding UTF-8 -cp "KnightOnline_402.jar;microemulator.jar" --release 7 HSOSKY<Ten>Support.java
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" HSOSKY<Ten>Patcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" HSOSKY<Ten>Patcher <input.jar> <output.jar>
```

Các patcher HSOSKY **nhận tham số** input/output, bỏ trống thì lấy giá trị mặc định trong
code. Patcher từ chối ghi đè output đã tồn tại — muốn build lại thì xoá file cũ hoặc đặt
tên khác.

Điểm dễ vướng: support class của HSOSKY biên dịch theo tên obfuscate của
**KnightOnline_402.jar** (`fu`, `et`, `bw`), rồi patcher dùng ASM remap owner sang tên
obfuscate của jar HSOSKY đang patch (HSOSKY dùng tên dài kiểu `IIllIIlIll...`). Đừng đổi
sang classpath jar HSOSKY, sẽ không compile.

## Jar trong project

| Jar | Vai trò |
|---|---|
| `HSOSKY.jar` | gốc, chưa mod — điểm bắt đầu nếu muốn dựng lại chuỗi từ đầu |
| `hsosky_mod.jar` | mốc giữa chuỗi, là input mặc định của `HSOSKYAutoEnhancePatcher` |
| `HSOSKY_MOD_AUTO_ENHANCE_GOLD_SERVER_GATED_NOFREEZE_V8.jar` | input của `HSOSKYZoneChatPatcher` |
| `HSOSKY_MOD_AUTO_ENHANCE_GOLD_ZONECHAT_V9.jar` | **bản HSOSKY mới nhất, đang chơi** — mod mới nên patch từ đây |
| `KnightOnline_402.jar` | gốc, chưa mod |
| `KnightOnline_402_Modded.jar` | output của `GhostModPatcher` |
| `KnightOnline_402_Modded_AUTO_ENHANCE_100MS.jar` | output của `KOAutoEnhancePatcher` |
| `asm-9.7.jar`, `asm-tree-9.7.jar` | thư viện patch bytecode |
| `cfr-0.152.jar` | decompiler, dùng để đọc bytecode đã patch |
| `microemulator.jar`, `micro-gdl-zang.jar` | giả lập để chạy thử jar |

Các jar trung gian của chuỗi HSOSKY (10SKILL → FASTCAST → AUTOFEED → … → V4) đã xoá.
Source của những patcher đó vẫn còn làm tham khảo; muốn dùng lại thì trỏ input sang V9
thay vì jar trung gian đã mất.

## Chạy thử jar

```powershell
java -cp "microemulator.jar;<jar>.jar" org.microemu.app.Main <jar>.jar
```

## Đọc bytecode

```powershell
java -jar cfr-0.152.jar <jar>.jar --outputdir src-<ten>-cfr
```

`src-hsosky-cfr/` là HSOSKY đã decompile, `src-decompiled/` và `src-jadx-deobf/` là
KnightOnline. Giữ lại để tra cứu, không phải để build.

## Tài liệu

- `PROTOCOL.md` — protocol mạng HSOSKY (frame, handshake, keystream, bảng opcode) và các
  kỹ thuật mod đã verify. Đọc trước khi làm mod mới liên quan tới packet.
- `HSOSKY_COMBAT_ANALYSIS.md` — phân tích luồng combat.
- `HSOBridgeApi.java` — interface cầu nối để mod gọi vào code đã obfuscate; xem
  `HSOReviveNorthPatcher.java` làm ví dụ sinh class bridge bằng ASM.
