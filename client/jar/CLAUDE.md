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

## Build GhostMod & Repoint Client (KnightOnline)

Cách gọn nhất, chạy tự động biên dịch, nhúng mod và trỏ server (mặc định `127.0.0.1`):

```powershell
.\build.ps1
# hoặc chỉ định IP server khác (ví dụ VPS EC2):
.\build.ps1 -ServerIp 13.229.47.78
```

Lệnh trên sinh ra:
- `KnightOnline_402_Local.jar`: Bản gốc chuẩn v4.0.2 đã trỏ host về server.
- `KnightOnline_402_Modded_Local.jar`: Bản mod (GhostMod 10 phím tắt, 50 FPS, auto buff, toạ độ) đã trỏ host về server.

Patcher tự verify lại jar vừa ghi rồi mới publish.

**AUTOMATION RULE:** sửa `GhostMod.java` hoặc `GhostModPatcher.java` là chạy ngay `.\build.ps1`
để jar mod không bị lệch với source.

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
| `Luna_Mod Truongbk.jar` | mod TruongBk gốc (bên thứ ba), base của chuỗi HSOLUNA_TRUONGBK |
| `HSOLUNA_TRUONGBK_PERFECT.jar` | **bản HSOLUNA đang chơi** — 12 skill, fast cast, noclip, speed 2x/run 1.25x |
| `HSOLUNA_TRUONGBK_AUTOTRADE.jar` | bản Auto Đi Buôn đầu tiên chạy thông cả vòng — chỉ mua kim cương |
| `HSOLUNA_TRUONGBK_AUTOTRADE_V2.jar` | thêm: hết kim cương thì mua ruby |
| `HSOLUNA_TRUONGBK_AUTOTRADE_V3.jar` | gom việc đặt lại trạng thái theo chuyến về một chỗ |
| `HSOLUNA_TRUONGBK_AUTOTRADE_V5.jar` | như dưới nhưng `MAX_GEMS = 1` (bản chạy thử) |
| `HSOLUNA_TRUONGBK_AUTOTRADE_V7.jar` | như dưới nhưng ngưỡng "bò tụt lại" chưa nhân đôi khi bật tăng tốc |
| `autotrade_v11.jar` | bản Auto Đi Buôn cũ |
| `autotrade_v11.1.jar` | bản song song của v11 — RMS tách hẳn (`HSOLUNA2` / `Truongbk2` / tiền tố store `B_`) |
| `autotrade_v15.jar` | giãn nhịp 1s/8s khi gọi–huỷ bò, không kết luận mất bò từ một khung hình, không vơ nhầm bò người khác |
| `autotrade_v15.1.jar` | bản song song của v15 (`HSOLUNA_V151` / `Truongbk_V151` / `C_`) |
| `autotrade_v16.jar` | mất bò thì **làm lại chuyến** chứ không tắt auto (server bảo trì 2 lần/ngày là mất bò) |
| `autotrade_v16.1.jar` | v16 với RMS của `autotrade_v15.1.jar` (`HSOLUNA_V151` / `Truongbk_V151` / `C_`) |
| `autotrade_v17.jar` | — không vơ nhầm bò người khác lúc gọi, bắt được câu Dylan từ chối, **dừng hẳn rồi mới bấm Bò vàng** (hết trôi theo quán tính), nhịp gọi bò 1s → 0,5s, tên bước có dấu |
| `autotrade_v17.1.jar` | v17 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v18.jar` | v17 với ngưỡng "bò tụt lại" lúc tăng tốc **160 → 200px** |
| `autotrade_v18.1.jar` | v18 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v19.jar` | v18 + bò tụt nhịp thì **đứng yên chờ** thay vì quay đầu đi đón (hết quay đầu oan ở cổng) |
| `autotrade_v19.1.jar` | v19 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v20.jar` | v19 + nhận diện bò theo **tên chủ** (tên thắng mọi phỏng đoán), hết vồ nhầm bò người khác rồi đứng im |
| `autotrade_v20.1.jar` | v20 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v21.jar` | **hỏng — đừng dùng.** Đón bò theo từng đoạn ngắn, hở ra dải 70..100px không ai phát lệnh đi → kẹt hẳn ở cổng |
| `autotrade_v21.1.jar` | hỏng như v21 |
| `autotrade_v22.jar` | logic dắt bò trở lại **đúng hình v11** (bỏ hết cơ chế đứng-chờ), ngưỡng lúc tăng tốc **240px** |
| `autotrade_v22.1.jar` | v22 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v22_x3.jar` | v22 **nhân ba** — 193 lớp ở gói mặc định + `b/` + `c/`, ba ô lưu RMS riêng nên đăng nhập ba nhân vật cùng lúc |
| `autotrade_v23.jar` | v22 + **công tắc Noclip** trong menu Auto Đi Buôn: tắt thì tìm đường thật thay vì đi xuyên tường |
| `autotrade_v23_x3.jar` | v23 nhân ba — **tắt Noclip thì chạy vòng vòng**, đừng dùng |
| `autotrade_v24.jar` | v23 + tính đường **một lần rồi đi theo** khi tắt Noclip (hết chạy vòng vòng) |
| `autotrade_v24_x3.jar` | v24 nhân ba — **tắt Noclip thì lảng vảng quanh cổng**, không sang map |
| `autotrade_v25.jar` | v24 + vào **đúng cổng**: hết đường thật thì bước thẳng như engine gốc; xoá điểm lùi-ra-cổng khi đổi map |
| `autotrade_v25_x3.jar` | v25 nhân ba — **vẫn không vào được cổng** khi tắt Noclip |
| `autotrade_v26.jar` | v25 + **ép bước cuối lên ô cổng** (đường một phần tử, con trỏ 0) như `GhostMod.forcePortalStep` |
| `autotrade_v26_x3.jar` | v26 nhân ba — **tắt Noclip: map lớn không tìm được đường, map nhỏ tới gần cổng thì quay đầu** |
| `autotrade_v27.jar` | **bản đang chơi** — v26 + **tự dò rộng (BFS) trên bản đồ ô thật** khi engine chịu thua, bám kế hoạch lệch tối đa một ô |
| `autotrade_v27.1.jar` | v27 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `KNIGHT_ONLINE_RL.jar` | V3 → server **"Rồng Lửa"** `27.0.12.133:19129`, MIDlet đổi tên, auto đập đồ giãn 4s |
| `KNIGHT_ONLINE_RL_V3.jar` | dựng từ V5 (bản chạy thử 1 viên) |
| `KNIGHT_ONLINE_RL_V5.jar` | dựng từ V7 |
| `KNIGHT_ONLINE_RL_V13.jar` | **bản Rồng Lửa mới nhất** — dựng từ V15 |
| `asm-9.7.jar`, `asm-tree-9.7.jar` | thư viện patch bytecode |
| `cfr-0.152.jar` | decompiler, dùng để đọc bytecode đã patch |
| `microemulator.jar`, `micro-gdl-zang.jar` | giả lập để chạy thử jar |

Các jar trung gian của chuỗi HSOSKY (10SKILL → FASTCAST → AUTOFEED → … → V4) đã xoá.
Source của những patcher đó vẫn còn làm tham khảo; muốn dùng lại thì trỏ input sang V9
thay vì jar trung gian đã mất.

## Build Auto Đi Buôn (HSOLUNA_TRUONGBK)

Thêm mục "Auto Đi Buôn" vào menu "TruongBk MOD". Chi tiết trong `AUTOTRADE_NOTES.md`.

```powershell
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" VerifyAutoTradeSymbols
java  GenAutoTradeSupport
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" MakeAutoTradeCompileJar
javac -encoding UTF-8 -cp "HSOLUNA_TRUONGBK_PERFECT.jar;microemulator.jar" --release 7 HSOSKYRuntimeSupport.java
javac -encoding UTF-8 -cp ".autotrade-compile.jar" --release 7 HSOAutoTradeSupport.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher HSOLUNA_TRUONGBK_PERFECT.jar HSOLUNA_TRUONGBK_AUTOTRADE_V<n>.jar
```

Patcher **từ chối ghi đè** output đã tồn tại, nên mỗi bản sửa là một tên jar mới (`_V2`, `_V3`…),
giữ nguyên bản cũ để còn lùi lại được. Bỏ trống tham số thì nó lấy tên mặc định
`HSOLUNA_TRUONGBK_AUTOTRADE.jar`.

Patcher đổi thêm một thứ ngoài năm móc của mod: **nhịp tự đăng nhập lại của chính client
`30s → 15s`** (auto login của bản mod thì vốn đã 15s). Nó chốt theo cấu trúc — lớp gọi
`HSOSKYPetFeedSupport.onDisconnected()`, mẫu `[LDC 30000L, LADD, PUTSTATIC]` vào đúng field hẹn
giờ của lớp đó — chứ không đổi bừa mọi hằng số 30000, vì trong jar còn một chỗ hẹn 60 giây của
field khác.

Ba điểm dễ vướng riêng của mod này:

- **`HSOAutoTradeSupport.java` là file sinh ra**, bản để sửa là `HSOAutoTradeSupport.java.in`
  (dùng dấu `@khoá@`). Bảng ký hiệu cũng là file sinh ra — thêm ký hiệu mới bằng các công cụ
  `Add*Symbols` (`AddInfoSymbols` tra dòng thông báo chạy ở đầu màn hình), rồi
  `VerifyAutoTradeSymbols` đối chiếu lại với jar.
- Phải biên dịch với `.autotrade-compile.jar`, **không** phải jar gốc. Support class đọc hai
  field `private` của màn hình cửa hàng; `MakeAutoTradeCompileJar` mở quyền hai field đó cho bản
  biên dịch, `AutoTradePatcher` mở đúng hai field đó trong jar xuất ra.
- **Đừng biên dịch lại `HSOSKYPetFeedSupport.java` rồi nhét vào jar** — source trên đĩa cũ hơn
  class trong jar, ghi đè là mất `onCanvasUpdate` / `handleModMenuAction` / RMS. Patcher chỉ đổi
  **hai byte version** của class đó (61 → 51) và verify mọi byte còn lại y nguyên: class 61 nghĩa
  là jar chỉ chạy trên JRE ≥ 17, giả lập chạy bằng Java 8 báo `UnsupportedClassVersionError`.

## Dựng bản x3 (ba thể hiện trong một jar)

`autotrade_v*_x3.jar` là chính bản mod được **nhân ba**: 193 lớp ở gói mặc định, 193 bản sao ở `b/`,
193 ở `c/`, mỗi bản một ô lưu RMS riêng. Sửa một lớp support thì phải thay đủ **cả ba** bản, và
hai bản `b/` `c/` tham chiếu tới các lớp đã đổi tên theo gói.

```powershell
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" RepackSupportX3 autotrade_v22_x3.jar autotrade_v26_x3.jar HSOAutoTradeSupport HSOSKYRuntimeSupport
```

Công cụ **tự kiểm trước khi ghi**: nó chạy phép đổi gói lên lớp CŨ của chính jar đầu vào rồi so với
lớp `b/` `c/` cũ đang nằm sẵn trong đó. Trùng nhau nghĩa là phép đổi tên tái tạo đúng những gì công
cụ gốc đã làm. Sau khi ghi còn kiểm lại: mọi lớp được tham chiếu phải có thật trong chính jar đó.

Không có `asm-commons` trong thư mục nên phép đổi gói viết tay trên `ClassNode` — đủ cho CLDC
(không có `invokedynamic`; gặp thì công cụ ném lỗi chứ không đoán).

## Tạo một bản chạy song song (RMS tách hẳn)

```powershell
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradeV11_1Patcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradeV11_1Patcher autotrade_v23.jar autotrade_v23.1.jar HSOLUNA_V151 Truongbk_V151 C_
```

Ba tham số cuối là **định danh riêng** của bản song song: `MIDlet-Name`, `MIDlet-Vendor`, và
tiền tố tên record store. **Mỗi bản song song phải có một bộ riêng** — dùng lại bộ của bản trước
(`HSOLUNA2` / `Truongbk2` / `B_`) là hai bản lại dùng chung dữ liệu. Bỏ trống ba tham số thì lấy
đúng bộ cũ của v11.1.

Vì sao phải **hai** lớp cách ly chứ không chỉ đổi tên MIDlet: theo chuẩn MIDP, phạm vi RMS gắn
với cặp (vendor, MIDlet-Name) — nhưng có giả lập (KEmulator và vài bản khác) gộp RMS chung một
chỗ theo **tên store**. Chỉ đổi tên MIDlet thì trên những giả lập đó hai bản vẫn dùng chung dữ
liệu. Lớp thứ hai gắn tiền tố vào mọi tên store, vá đúng ba chỗ đã tra bằng bytecode:
`TemMidlet.save/load`, hàm "được phép xoá" của lớp tiện ích (không vá thì bản mới xoá cả store
của bản cũ), và store riêng `hsosky_cfg` của mod.

## Trỏ sang server Rồng Lửa

```powershell
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" RongLuaRepointPatcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" RongLuaRepointPatcher autotrade_v15.jar KNIGHT_ONLINE_RL_V13.jar
```

Danh sách server nằm trong `<clinit>` của một lớp, dạng `String[][]` mỗi dòng là `{tên, host}`.
Host bị giấu bằng cách **XOR từng ký tự với 5** (`"456+42=+760+=6"` → `103.178.235.83`) nên phải
thay **cả lời gọi hàm giải mã** chứ không sửa được chuỗi. Cổng thì bản gốc đã dùng sẵn `19129`
— patcher kiểm lại và báo lỗi nếu khác, để không âm thầm trỏ nhầm cổng.

Patcher đổi **ba** thứ, không hơn:

1. Tên server trong danh sách + host.
2. `MIDlet-Name` / `MIDlet-1` → `KNIGHT_ONLINE_RL`. **Phạm vi RMS gắn với (vendor, MIDlet-Name)**
   nên đổi tên là mất hết thiết lập mod và thông tin đăng nhập đã lưu — người chơi đã đồng ý.
3. Nhịp auto đập đồ: `HSOSKYAutoEnhanceSupport.settleDelayMs` `100ms → 4000ms`. Đây là **cổng
   duy nhất** giữa hai lần đập (`isResultSettled()`), và nó đếm từ lúc **server trả kết quả** nên
   áp dụng như nhau dù lần trước lên hay không. Không nơi nào gọi `setSettleDelayMs` nên hằng số
   trong `<clinit>` là nguồn duy nhất.

Lớp đập đồ được **vá thẳng trong jar**, không biên dịch lại từ `HSOSKYAutoEnhanceSupport.java`
trên đĩa — cùng lý do với `HSOSKYPetFeedSupport`: source trên đĩa có thể đã lệch với class trong
jar.

Nó tự verify: đúng 2 dòng đổi tên server, đúng 2 dòng trỏ host, không sót `"LUNA"`, cổng còn
nguyên, manifest hết tên cũ, nhịp đập đồ đúng 4000ms, và **mọi entry khác byte-identical** với
jar đầu vào (chứng minh Auto Đi Buôn và các mod cũ không bị đụng).

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
- `AUTOTRADE_NOTES.md` — Auto Đi Buôn: bảng opcode gói tin của HSOLUNA, cách tra tên obfuscate
  đã verify, 5 móc ASM, và hai ràng buộc của server khi dắt pet đi buôn. Ghi theo từng bản,
  mỗi mục là một lần sửa kèm lý do.
- **`AUTOTRADE_HAUL.html`** — **chặng dắt bò (bước 6), trạng thái hiện tại**: ba luật của server
  mà mọi hằng số là hệ quả, tuyến 52→8, thứ tự kiểm tra trong một nhịp dắt, bảng hằng số
  kèm ý nghĩa, ba vấn đề của việc qua cổng, bảng xử lý mất bò, và những chỗ còn hở.
  Mở bằng trình duyệt — **file local, không publish đi đâu**. Đọc trước khi đụng vào
  `stepHaul` / `stepToward` / `routeTowards`: gần như mọi con số ở đó đều có một lần đặt sai
  đứng sau nó, ghi trong mục "Bốn lần đã đặt sai".
- `HSOBridgeApi.java` — interface cầu nối để mod gọi vào code đã obfuscate; xem
  `HSOReviveNorthPatcher.java` làm ví dụ sinh class bridge bằng ASM.
