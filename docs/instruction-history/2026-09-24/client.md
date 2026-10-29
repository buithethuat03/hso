# Mod client J2ME — HSOSKY và KnightOnline

## Quy định thư mục phát hành (2026-09-24)

`D:\project\hso\client\jar` chỉ chứa một file JAR phát hành đã đạt yêu cầu, trong trường hợp khác server (ip), cho phép nhiều hơn một file nằm trong thư mục trên, nhưng yêu cầu mỗi server chỉ chứa duy nhất 1 file.

Các file jar cũ sẽ được backup trong `D:\project\hso\client\jar\backup` trước khi ghi đè file mới lên file cũ.

Mã nguồn, tài liệu, baseline, dependency, manifest, file build tạm
nằm trong `D:\project\hso\client\workspace`, theo đúng các thư mục `src/`,
`docs/`, `fixtures/`, `symbols/`, `tools/`, `build/` và `inputs/`. Không tạo
file phụ trong `client/jar`.

Chạy build từ `client/workspace` bằng `tools/luna/build-source.ps1`. Script
đưa JAR vào `client/jar` **sau khi** các bước kiểm tra thành công và chuyển
JAR phát hành cũ sang `client/workspace/inputs/releases/`. Khi bản mới đạt
yêu cầu, cập nhật **ngay** `Last verified`, version matrix, Feature map và
Known invariants trong file này bằng tên JAR, SHA-256, manifest và kết quả
kiểm tra thực tế; đồng bộ thông tin bản đang phát hành trong `../../CLAUDE.md`.
Không ghi bản thử chưa được xác nhận là release đã đạt yêu
cầu; nêu rõ phần kiểm tra còn thiếu (ví dụ thử với server thật).

> AI bắt đầu ở mục **Luna canonical AI guide** gần cuối file. Mục đó là
> cookbook hiện hành; các phần lịch sử phía trên chỉ dùng để tra cứu tương thích.

Hai game riêng, cùng một cách làm: dùng ASM patch bytecode của jar game, nhúng thêm một
"support class" viết bằng Java thường vào trong jar.

**Mọi lệnh build hiện hành trong tài liệu này chạy với thư mục hiện tại là `client/workspace`.**
Pipeline lịch sử `tools/legacy/ghostmod/build.ps1` hardcode tên jar theo đường dẫn tương đối
và không phải quy trình phát hành. Pipeline Luna hiện tại là
`tools/luna/build-source.ps1` và phải chạy từ `client/workspace`.

Mọi `.class` trong project đều là **artifact build**, không commit, sinh lại bằng lệnh
dưới. Xoá sạch `*.class` là chuyện bình thường.

## Quy tắc chung — luôn dùng `-encoding UTF-8`

Source có comment và string tiếng Việt. Máy này mặc định `windows-1258`, thiếu cờ đó là
javac báo `unmappable character` và fail. **Mọi** lệnh `javac` trong project phải có
`-encoding UTF-8`.

Support class nhúng vào jar phải biên dịch với `--release 7` (môi trường J2ME/MIDP).
Patcher chạy trên JDK thường nên không cần.

## Historical GhostMod & Repoint Client pipeline

The legacy sources are under `tools/legacy/ghostmod/` and their input JARs
are intentionally not retained. The canonical maintained pipeline is
`tools/luna/build-source.ps1`; use the legacy script only as historical
reference.

Cách gọn nhất, chạy tự động biên dịch, nhúng mod và trỏ server (mặc định `127.0.0.1`):

```powershell
.\tools\legacy\ghostmod\build.ps1
# hoặc chỉ định IP server khác (ví dụ VPS EC2):
.\tools\legacy\ghostmod\build.ps1 -ServerIp 13.229.47.78
```

Lệnh trên sinh ra:
- `KnightOnline_402_Local.jar`: Bản gốc chuẩn v4.0.2 đã trỏ host về server.
- `KnightOnline_402_Modded_Local.jar`: Bản mod (GhostMod 10 phím tắt, 50 FPS, auto buff, toạ độ) đã trỏ host về server.

Patcher tự verify lại jar vừa ghi rồi mới publish.

**AUTOMATION RULE (historical):** sửa `tools/legacy/ghostmod/GhostMod.java`
hoặc `GhostModPatcher.java` chỉ có ý nghĩa khi phục hồi đủ input JAR và
dependency bên ngoài; pipeline này không còn là đường build hiện hành.

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

Điểm dễ vướng: support class của HSOSKY biên dịch theo symbol catalog của
baseline compile JAR, rồi patcher dùng ASM remap owner sang catalog của JAR đang patch.
Không chép tên binary vào feature source; nếu symbol không có trong catalog thì dừng và
cập nhật bridge/verifier theo đúng owner + descriptor.

## Historical JAR notes (not current repository contents)

The table below records the old patch chain for forensic reference only. Those
historical JARs were intentionally removed; the current repository contains
only `luna_final.jar`. Do not treat a name in this table as a file that should
exist before starting work.

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
| `autotrade_v27.jar` | v26 + **tự dò rộng (BFS) trên bản đồ ô thật** khi engine chịu thua, bám kế hoạch lệch tối đa một ô |
| `autotrade_v27.1.jar` | v27 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v28.jar` | v27 + mục menu **"Auto Ngã 4"** — **tìm thiên thạch bằng thực thể, hỏng, đừng dùng** |
| `autotrade_v28.1.jar` | v28 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v29.jar` | Auto Ngã 4 đứng vào chính giữa bản đồ — **đứng nhìn, quái xa quá không đánh được** |
| `autotrade_v29.1.jar` | v29 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v30.jar` | Auto Ngã 4 ra đón quái ở cửa — nhưng đứng sát tường, và vẫn khoá mục tiêu ở tận xa |
| `autotrade_v30.1.jar` | v30 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v31.jar` | lùi ra khỏi cửa 350px + tầm chọn mục tiêu gốc — nhưng **đuổi theo quái nên chạy vòng vòng** |
| `autotrade_v31.1.jar` | v31 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v32.jar` | Auto Ngã 4 đứng đón một chỗ, thôi đuổi theo quái; HUD ghi rõ đang đón cửa nào |
| `autotrade_v32.1.jar` | v32 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v33.jar` | chỗ đón sát cửa hơn: cách tường **80px** — nhưng vẫn chạy vòng vòng |
| `autotrade_v33.1.jar` | v33 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v34.jar` | khoá đổi cửa 20s, dừng hẳn ba tầng, giữ Noclip — nhưng xong lượt thì không về được chỗ NPC |
| `autotrade_v34.1.jar` | v34 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v35.jar` | về chỗ đã đứng lúc bật auto — nhưng chỗ đó ghi lúc nhân vật còn đang trôi |
| `autotrade_v35.1.jar` | v35 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v36.jar` | tự neo lại chỗ bắt chuyện khi menu NPC mở — nhưng vào phó bản còn chạy sang cửa sai |
| `autotrade_v36.1.jar` | v36 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v37.jar` | quên sạch hộp thông báo khi đổi map, hết chạy sang cửa của lượt trước |
| `autotrade_v37.1.jar` | v37 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v38.jar` | vào phó bản đứng yên chờ hướng; ra làng trả lại Noclip; tầm chọn mục tiêu tính bằng pixel |
| `autotrade_v38.1.jar` | v38 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v39.jar` | chỗ đón lùi ra 100px so với tường (60 + 40) |
| `autotrade_v39.1.jar` | v39 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v40.jar` | tầm chọn mục tiêu trả lại toàn bản đồ |
| `autotrade_v40.1.jar` | v40 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `autotrade_v41.jar` | đợi bản đồ nạp xong rồi mới tính đường; neo chỗ đứng chặt hơn |
| `autotrade_v41.1.jar` | v41 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v7.jar` | dòng jar **chỉ có Auto Ngã 4** (ẩn menu Auto Đi Buôn, vòng lặp 16ms). Bản cuối của lối cũ: chỉ đợi map khi ra làng, bán kính tròn, suy cửa từ vị trí quái |
| `auto_n4_v8.jar` | viết lại Auto Ngã 4 theo 4 bước cố định (B1 về (588,564) → B2 nói chuyện → B3 chờ hướng / ra chỗ đón cố định / đứng đánh trong vòng tròn → B4 đợi 1s rồi lặp). Còn dừng gấp giữa đường |
| `auto_n4_v8.1.jar` | v8 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v9.jar` | v8 + **không cắt đường giữa chừng**: engine đi hết đường đã giao, tự dừng, rồi mới xét tới nơi → đợi 0.5s → tương tác. Bỏ hẳn `n4Park`/`n4StandStill` |
| `auto_n4_v9.1.jar` | v9 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v10.jar` | chặn Auto Tự Đánh ghi `player.path`. Hai auto cùng giành một biến chính là lỗi "chạy vòng vòng". Đứng im đánh nên vật phẩm rơi xa không nhặt được |
| `auto_n4_v10.1.jar` | v10 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v11.jar` | trả quyền di chuyển cho Auto Tự Đánh — không đủ: nó dừng ngay khi vào tầm đánh nên đồ vẫn rơi xa |
| `auto_n4_v11.1.jar` | v11 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v12.jar` | **bản đang chơi** — tự bám tới tận xác quái (30px) rồi nán 1.5s nhặt đồ, xong mới về chỗ đón |
| `auto_n4_v12.1.jar` | v12 với ô lưu `HSOLUNA_V151` / `Truongbk_V151` / `C_` |
| `auto_n4_v10_luna_combat_fix12.jar` | giữ vòng đánh native, cắm 1 móc đường đi + 4 móc lọc mục tiêu của `HSOAutoTradeSupport`. **Đánh quái toàn bản đồ, không chạy tới gần** |
| `auto_n4_v10_luna_combat_fix13.jar` | **thiếu — đừng dùng.** Mới sửa `PLAYER` (tầm chọn mục tiêu), chưa gỡ lệnh `goto` bỏ qua bước chạy tới quái ở lớp thi triển kỹ năng, nên vẫn đánh toàn bản đồ |
| `auto_n4_v10_luna_combat_fix14.jar` | logic **Auto Tự Đánh trả về đúng bản `Luna (2).jar`**, cả ba lớp. Giữ lại 4 mod ngoài combat: nhặt đồ từ xa, bỏ gói tin dậm chân, thanh kỹ năng 12 ô, cast nhanh 100ms |

**Ba chỗ đã làm hỏng Auto Tự Đánh** (đều do `HSOLUNA_TRUONGBK_PERFECT.jar` đưa vào, mọi bản
`auto_n4_*` đều thừa hưởng):

1. `PLAYER` — tầm chọn mục tiêu `tầm đánh × 3/2` → `2000000` (cả bản đồ), luật bỏ mục tiêu khi
   trôi quá `240px` khỏi điểm neo → `2000000`, đồng hồ gỡ kẹt `500` nhịp → `2000000`, và bốn
   chỗ lọc mục tiêu `entity.kind == 1` bị thay bằng móc `autoTargetGate`.
2. **Lớp thi triển kỹ năng** — chèn một lệnh `goto` ngay đầu hàm *"đưa nhân vật vào tầm rồi mới
   đánh"*. Hàm gốc: xa hơn tầm kỹ năng thì tính một chỗ đứng cách mục tiêu đúng tầm, tìm đường,
   **giao đường cho nhân vật chạy tới**; trong tầm thì đánh luôn. `goto` nhảy thẳng sang nhánh
   "đánh luôn" ⇒ đánh được quái ở bất kỳ đâu mà không bước một bước nào. Đây mới là nguyên nhân
   chính của "đánh quái toàn bản đồ"; sửa mỗi `PLAYER` không đủ.
3. **Lớp menu mod** — bán kính vòng tròn xanh `tầm đánh × 3/2` → `× 9/10`. Vòng tròn đó là
   **tầm tìm quái**, không phải tầm đánh; vẽ nhỏ hơn tầm thật thì nhìn ra ngoài thành "nó đánh
   cả quái ngoài vòng tròn". Lớp này đã bị dựng lại (đổi tên hết field) nên không gấp cả hàm
   với bản Luna được — chỉ đổi riêng hai số nhân/chia rồi đối chiếu năm lệnh đó.

Lệnh dựng bản `fix14` — công cụ **gấp từng hàm** của hai lớp đầu với bản tham chiếu rồi lấy
nguyên phần của bản tham chiếu cho mọi chỗ lệch, trừ những chỗ đã khai báo giữ lại (danh sách
giữ **tách riêng theo từng lớp**: `goto` chèn thêm ở `PLAYER` là mod dậm chân phải giữ, còn
`goto` cùng hình dạng ở lớp thi triển kỹ năng chính là chỗ phải gỡ). Sau khi ghi nó gấp lại lần
nữa từ chính byte vừa sinh và bắt buộc chỉ còn đúng những chỗ giữ đó — lệch thêm là ném lỗi,
không ghi file:

```powershell
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" LunaExactAutoAttackPatcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" LunaExactAutoAttackPatcher auto_n4_v10_luna_combat_fix12.jar "Luna (2).jar" auto_n4_v10_luna_combat_fix14.jar
```

Lệnh dựng dòng `auto_n4_*` (khác dòng `autotrade_*` ở tham số thứ ba):

```powershell
java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher HSOLUNA_TRUONGBK_PERFECT.jar auto_n4_v9.jar hide-trade-menu
java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradeV11_1Patcher auto_n4_v9.jar auto_n4_v9.1.jar HSOLUNA_V151 Truongbk_V151 C_
```
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

Phần patcher cũ bên dưới là **lịch sử**; các JAR input trung gian đã được dọn và
dependency ASM/microemulator không còn nằm trong repo. Pipeline được hỗ trợ hiện tại
chạy từ `client/workspace`, dùng `inputs/luna_final-golden-baseline.jar` làm baseline và nhận dependency ngoài:

```powershell
.\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps
```

The current pipeline keeps the native `Tự động` menu available and keeps the
semantic one-shot post-login ENTER hook. The optional `-EnterOnly` switch is
retained only for backwards-compatible diagnostics and is a no-op in the
source build. It reads `inputs/luna_final-golden-baseline.jar`; `../jar/luna_final.jar` is
the current stable release. The final JAR additionally gates custom reconnect
on `selectedAutoLoginChar >= 0`, so manual login never starts the 10-second
custom reconnect watchdog.

Chi tiết generator/bridge lịch sử nằm trong `AUTOTRADE_NOTES.md`; source legacy đã
được gom dưới `tools/legacy/luna/`.

Patcher **từ chối ghi đè** output đã tồn tại, nên mỗi bản sửa là một tên jar mới (`_V2`, `_V3`…),
giữ nguyên bản cũ để còn lùi lại được. Bỏ trống tham số thì nó lấy tên mặc định
`HSOLUNA_TRUONGBK_AUTOTRADE.jar`.

Patcher đổi thêm một thứ ngoài năm móc của mod: **nhịp tự đăng nhập lại của chính client
`30s → 15s`** (auto login của bản mod thì vốn đã 15s). Nó chốt theo cấu trúc — lớp gọi
`HSOSKYPetFeedSupport.onDisconnected()`, mẫu `[LDC 30000L, LADD, PUTSTATIC]` vào đúng field hẹn
giờ của lớp đó — chứ không đổi bừa mọi hằng số 30000, vì trong jar còn một chỗ hẹn 60 giây của
field khác.

Ba điểm dễ vướng riêng của mod này:

- **`HSOAutoTradeSupport.java` là file sinh ra**, bản ABI là `HSOAutoTradeSupport.java.in`;
  feature source nằm trong `src/luna/` và bridge template nằm trong `tools/legacy/luna/legacy-hook.template`
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

# Luna canonical AI guide (read this section first)

This is the operating document for `client/workspace`. Run every current build command from this
directory. Read this section, then `symbols/luna-4.0.2.properties`,
`docs/LUNA_ARCHITECTURE.md`, the owning `src/luna/` module, and its fixture.
Only open a hook patcher when the requested change needs binary access.

Last verified: 2026-09-24 (HSO CHILD run cadence changed from 5/8 ticks, x1.25, to 8/8 ticks, x2; one class byte changed, all 946 JAR entries retained, 216 classes, manifest unchanged, JAR integrity and javap passed; live server movement and acceptance untested)

## Cleanup state (2026-09-19)

The active release is `../jar/luna_final.jar`; the source golden is preserved as
`inputs/luna_final-golden-baseline.jar`. Historical client JARs,
`.jar.bak*` files, decompiled Java trees, one-off root probes, generated
bridge sources, and compiled classes were removed. Do not recreate them in
`client/jar` just to inspect an old experiment.

The maintained client Java layout is:

- `src/luna/`: 20 semantic source files;
- `fixtures/luna/`: 3 deterministic host fixtures;
- `tools/luna/`: canonical build/verifier tools, including
  `XpPopupPatcher.java` for the versioned XP floating-text presentation;
- `tools/legacy/luna/`: legacy Luna/AutoTrade bridge, support, patcher, and
  route test sources;
- `tools/legacy/hsosky/`: historical HSOSKY/KO patchers and support classes;
- `tools/legacy/ghostmod/`: historical GhostMod sources and launcher;
- `tools/legacy/shared/`: old symbol helpers and shared probes;
- `client/workspace/inputs/`: baseline and preserved input JARs;
- `client/jar/`: the single verified release JAR only.

ASM 9.7, ASM Tree 9.7, and MicroEmulator are external build dependencies.
Store them outside the repository and pass their directory to
`build-source.ps1` with `-DependencyDir`, or set `LUNA_DEPS`. Do not commit
dependency JARs or generated output JARs.

`Downloads/` is a local mixed archive, not a build input or maintained source
tree. It was intentionally left untouched because it contains non-code files;
do not add pipeline dependencies on anything in that directory.

Root-level `.txt` files are disposable bytecode dumps or scratch output and
are ignored by policy. The only maintained client text trace is
`fixtures/luna/runtime-trace.txt`; the server data file `server/data/msg.txt`
belongs to the separate server pipeline and must be preserved.

## 1. Bắt đầu nhanh cho AI

The active release is `../jar/luna_final.jar`, manifest version `4.0.2`, SHA-256
`6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`.
The source baseline is `inputs/luna_final-golden-baseline.jar`, SHA-256
`06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F`.
It has 192 classes: 186 at class major 47 and 6 support classes at major 51.
Do not decompile when the versioned catalog covers the requested symbol.

The supported one-command source build is:

```powershell
.\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps
```

It chooses a fresh output name, refuses to overwrite an existing JAR, and
runs the semantic tests, symbol verification, bridge generation, patcher,
resource comparison, class loading, and legacy route regression checks.
The individual commands below are provided when diagnosing one stage.

```powershell
$semanticSources = @(rg --files src | Where-Object { $_ -like '*.java' })
$fixtureSources = @(rg --files fixtures/luna | Where-Object { $_ -like '*.java' })
$dependencyDir = $env:LUNA_DEPS
New-Item -ItemType Directory -Force build/semantic,build/test,build/tools | Out-Null
javac -encoding UTF-8 --release 7 -d build/semantic $semanticSources
javac -encoding UTF-8 --release 7 -cp build/semantic -d build/test $fixtureSources
java -cp "build/semantic;build/test" LunaSemanticHostTest
java -cp "build/semantic;build/test" LunaControllerHostTest
java -cp "build/semantic;build/test" LunaRouteHostTest
javac -encoding UTF-8 --release 7 -cp "$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" -d build/tools tools/luna/*.java
java -Xverify:all -cp build/tools luna.tools.LunaSymbolCatalogVerifier
java -Xverify:all -cp "build/tools;$dependencyDir\microemulator.jar" luna.tools.LunaJarVerifier inputs/luna_final-golden-baseline.jar `
  --load-classes --write-manifest build/luna-4.0.2.golden.manifest
```

`LunaControllerHostTest` covers the first-open path while the world screen is
still settling, the stable-screen re-arm, a persisted login-screen -> world
transition after logout, and re-enable after a disabled state. A successful
source build therefore proves the lifecycle policy without requiring a
decompile or a live binary session.

Never use `inputs/luna_final-golden-baseline.jar` as an output path.

## 2. Bản đồ kiến trúc

| Component | Ownership |
|---|---|
| `LunaRuntime` | Common lifecycle and frame ordering. |
| `AutoTradeController` | Auto Đi Buôn state machine and movement. |
| `N4Controller` | Auto Ngã 4 combat loop and movement. |
| `ReviveController` | Revive retry timing and death transition. |
| `PersistentHorseController` | First-open and post-login re-arm policy for persisted auto-horse. |
| `MovementArbiter` | Exclusive path ownership and priority. |
| `PathPlanner` | Straight, engine, BFS, and portal route boundary. |
| `WorldRoutePlanner` | Versioned portal graph and deterministic next-hop lookup. |
| `LunaBridgeApi` | Semantic binary-client read/write boundary. |
| `LunaSemanticHooks` | Binary-free dispatcher from menu/frame hooks to semantic controllers. |
| `HSOAutoTradeSupport` | ABI-compatible hook façade; no new business logic. |
| `src/luna/ui` | Semantic menu, HUD, and notification state. |

`LunaBridgeApi` fills reusable mutable `LunaPlayerView`, `LunaEntityView`,
`LunaShopView`, and `LunaMenuView` DTOs, and sends reusable `LunaAction`
commands. `LunaClock` is production-backed by `SystemLunaClock` and test-backed
by `FakeLunaClock`.

The hook façade keeps the method name/descriptor expected by the patcher. The
the old binary-facing implementation is generated as
`LunaGeneratedLegacyHooks` from `tools/legacy/luna/legacy-hook.template`; it is a
compatibility bridge only. New feature logic belongs in `src/luna`, and the
bridge may call semantic policies such as `PathPlanner`, `WorldRoutePlanner`,
`NoticeReader`, and the AutoTrade haul policy. AI sessions must not edit the
generated bridge or use its obfuscated names as feature API.

## 3. Định tuyến theo loại yêu cầu

| Yêu cầu | Module cần đọc |
|---|---|
| Thêm menu/HUD | `src/luna/ui`, hook façade |
| Sửa Auto Đi Buôn | `AutoTradeController`, `PathPlanner` |
| Sửa Auto Ngã 4 | `N4Controller`, `MovementArbiter` |
| Sửa combat/path conflict | `MovementArbiter`, `N4Controller` |
| Sửa shop/NPC/dialog | `LunaBridgeApi`, `NoticeReader` |
| Sửa symbol/ASM hook | `symbols/`, `tools/luna/` |
| Sửa JAR packaging | build manifest, `LunaJarVerifier` |

## 4. Quy trình thêm tính năng

1. Identify the module that owns the behavior.
2. Edit semantic source, never generated output.
3. Add a symbol only when binary access is genuinely required.
4. Add a hook spec when bytecode patching is required.
5. Add host/unit tests and binary verification.
6. Build a new JAR name; never overwrite an old JAR.
7. Check entry diff, resource allow-list, hook count, and class version.
8. Update Feature map, Known invariants, version matrix, and `Last verified`.

## 5. Symbol và bridge policy

- Lookup is always `owner + member + descriptor`; a binary name alone is invalid.
- Feature source and architecture docs must not contain binary obfuscated names.
- Do not expose private fields through the compile JAR as a feature API; use a generated bridge/accessor.
- `LunaBridgeGenerator` generates the semantic bridge contract; ASM patching resolves the selected catalog.
- A failed symbol verifier stops the build. Never guess another name.
- Current metadata is `symbols/luna-4.0.2.properties`; its generated map is
  `symbols/autotrade-symbols.properties`.

## 6. Generated files

Do not hand-edit generated bridge files, `build/generated/*`, generated
symbol reports/maps, compiled support
classes, `.autotrade-compile.jar`, build manifests, temporary compile JARs, or
output JARs. The hook facade source is
`tools/legacy/luna/HSOAutoTradeSupport.java.in`; the
generated bridge template is `tools/legacy/luna/legacy-hook.template`. Edit semantic
`src/luna` first and touch a template only when the binary ABI itself must
change. The generated hook facade must preserve its method names/descriptors.
The legacy bridge template is the generated compatibility boundary; semantic
`src/luna` is the source of record for all feature logic. The generated façade
must preserve its hook ABI.

## 7. Compatibility invariants

- MIDP 2.1 / CLDC 1.1.
- Support compile uses `-encoding UTF-8 --release 7`.
- No Java 8 API, lambda, stream, or large per-frame allocation.
- Never overwrite `luna_final.jar` or another existing output JAR.
- Do not change resources without declaring an allow-list entry.
- AutoTrade and N4 never write a path without `MovementArbiter` ownership.
- Do not conclude a pet is lost while map/dialog state is settling.
- The horse-selection menu is independent from the removed historical GhostMod
  feature; persisted auto-horse is loaded from RMS and re-armed on first open
  and after a login/world rising edge.
- `PersistentHorseController` may only re-arm on a stable game screen with no
  dialog. The bridge must preserve the native 2500ms horse-use throttle: the
  enable action sets the native mode and timestamp, while `checkAndMountHorse()`
  is called only by a later retry. This prevents the first frame after login
  from producing the client error “Hiện tại không thể sử dụng vật phẩm này” or
  stealing focus from the horse-selection menu. The exact horse-item-unavailable
  and other-mount-in-use modal messages are intercepted only while persistent
  auto-horse is enabled; they update the bounded controller state and return
  without opening a modal. A connecting/reconnect wait dialog also blocks the
  world-ready condition before any mount request.
- The current Luna output contains no GhostMod menu, captcha hook, or
  `GhostController`; `tools/legacy/ghostmod/` is historical only.
- Release requires hook count, class version, resource, and `-Xverify:all` checks.

## 8. Build và test cookbook

The semantic smoke test, catalog verifier, and golden manifest command are in
section 1. The legacy-compatible support/patch sequence below is historical;
the supported current path is the one-command build in section 8A.

```powershell
javac -encoding UTF-8 --release 7 -cp "asm-9.7.jar;asm-tree-9.7.jar" -d build/tools tools/luna/*.java
java -cp build/tools luna.tools.LunaSymbolCatalogVerifier
java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" VerifyAutoTradeSymbols luna_final.jar symbols/autotrade-symbols.properties
javac -encoding UTF-8 -d build/tools tools/legacy/luna/GenAutoTradeSupport.java
java -cp build/tools GenAutoTradeSupport tools/legacy/luna/HSOAutoTradeSupport.java.in symbols/autotrade-symbols.properties build/generated/HSOAutoTradeSupport.java
java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" MakeAutoTradeCompileJar luna_final.jar build/generated/.autotrade-compile.jar symbols/autotrade-symbols.properties
javac -encoding UTF-8 -cp "luna_final.jar;microemulator.jar" --release 7 -d build/generated tools/legacy/luna/HSOSKYRuntimeSupport.java
javac -encoding UTF-8 -cp "build/generated/.autotrade-compile.jar;microemulator.jar" --release 7 -d build/generated build/generated/HSOAutoTradeSupport.java

# Patch to a new output name; never use luna_final.jar as the second argument.
$outputJar = "luna-4.0.2-refactor-your-tag.jar"
$autoTradeInput = "build/generated/" + [IO.Path]::GetFileNameWithoutExtension($outputJar) + ".autotrade.jar"
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" -d build/tools tools/legacy/luna/AutoTradePatcher.java
java -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher luna_final.jar $autoTradeInput
java -Xverify:all -cp "build/tools;asm-9.7.jar;asm-tree-9.7.jar" `
  -Dluna.symbols=symbols/autotrade-symbols.properties `
  luna.tools.XpPopupPatcher $autoTradeInput $outputJar

# Generate the semantic bridge contract when a hook needs binary access.
javac -encoding UTF-8 --release 7 -d build/tools tools/luna/LunaBridgeGenerator.java
java -cp build/tools luna.tools.LunaBridgeGenerator

# Verify output, bytecode, resources, hook count, and write its manifest.
java -Xverify:all -cp "build/tools;microemulator.jar" `
  luna.tools.LunaJarVerifier $outputJar `
  --load-classes --compare-resources luna_final.jar `
  --allow-resource x1/mfont/tahoma_14b_green.png `
  --allow-resource mfont/tahoma_14b `
  --write-manifest ("build/" + [IO.Path]::GetFileNameWithoutExtension($outputJar) + ".manifest")
```

Manual microemulator run:

```powershell
java -cp "microemulator.jar;$outputJar" `
  org.microemu.app.Main $outputJar
```

Existing host route regression test:

```powershell
javac -encoding UTF-8 --release 7 -cp build/semantic -d build/test fixtures/luna/LunaRouteHostTest.java
java -Xverify:all -cp "build/semantic;build/test" LunaRouteHostTest
```

JVM bytecode-load smoke test (run this before the route test):

```powershell
java -Xverify:all -cp "build/tools;microemulator.jar" `
  luna.tools.LunaJarVerifier $outputJar --load-classes
```

If an input JAR is missing, stop and update the version matrix or fixture;
never point a command to a deleted historical artifact.

## 8A. Canonical source build (src -> JAR)

Run `.\tools\luna\build-source.ps1` from `client/workspace` for the supported
source-to-JAR build. It compiles every semantic source, generates the ABI
facade and binary bridge, packages the semantic classes, and patches a fresh
output JAR. The timestamped output is deliberate: the patcher refuses to
overwrite an existing JAR. Supply the external dependency directory:

```powershell
.\tools\luna\build-source.ps1 -DependencyDir C:\path\to\luna-deps
```

The expanded block below is the diagnostic equivalent of that script. Its
classpath examples assume `$dependencyDir` contains `asm-9.7.jar`,
`asm-tree-9.7.jar`, and `microemulator.jar`; do not copy those JARs back into
the repository.

```powershell
$buildTag = Get-Date -Format "yyyyMMdd-HHmmss"
$outputJar = "build/releases/luna-4.0.2-refactor-$buildTag.jar"
$autoTradeInput = "build/generated/" + [IO.Path]::GetFileNameWithoutExtension($outputJar) + ".autotrade.jar"
$xpPopupInput = $autoTradeInput
$manifestPath = "build/luna-4.0.2-$buildTag.manifest"
$dependencyDir = $env:LUNA_DEPS
New-Item -ItemType Directory -Force build/semantic,build/test,build/tools,build/generated,build/releases | Out-Null

$semanticSources = @(rg --files src | Where-Object { $_ -like '*.java' })
$fixtureSources = @(rg --files fixtures/luna | Where-Object { $_ -like '*.java' })
$toolSources = @(rg --files tools/luna | Where-Object { $_ -like '*.java' })
javac -encoding UTF-8 --release 7 -d build/semantic $semanticSources
javac -encoding UTF-8 --release 7 -cp build/semantic -d build/test $fixtureSources
java -cp "build/semantic;build/test" LunaSemanticHostTest
java -cp "build/semantic;build/test" LunaControllerHostTest
java -cp "build/semantic;build/test" LunaRouteHostTest
javac -encoding UTF-8 --release 7 -cp "$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" -d build/tools $toolSources
java -cp build/tools luna.tools.LunaBridgeGenerator
java -Xverify:all -cp "build/tools;microemulator.jar" luna.tools.LunaSymbolCatalogVerifier

javac -encoding UTF-8 --release 7 -cp "$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" `
  -d build/tools tools/legacy/luna/VerifyAutoTradeSymbols.java
java -Xverify:all -cp "build/tools;$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" `
  VerifyAutoTradeSymbols inputs/luna_final-golden-baseline.jar symbols/autotrade-symbols.properties
javac -encoding UTF-8 -d build/tools tools/legacy/luna/GenAutoTradeSupport.java
java -Xverify:all -cp build/tools GenAutoTradeSupport `
  tools/legacy/luna/HSOAutoTradeSupport.java.in `
  symbols/autotrade-symbols.properties build/generated/HSOAutoTradeSupport.java
javac -encoding UTF-8 --release 7 -cp "inputs/luna_final-golden-baseline.jar;$dependencyDir\microemulator.jar;build/semantic" `
  -d build/generated build/generated/LunaGeneratedLegacyHooks.java
javac -encoding UTF-8 --release 7 -cp "inputs/luna_final-golden-baseline.jar;$dependencyDir\microemulator.jar" `
  -d build/generated tools/legacy/luna/HSOSKYRuntimeSupport.java
javac -encoding UTF-8 --release 7 -cp "$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" `
  -d build/tools tools/legacy/luna/MakeAutoTradeCompileJar.java tools/legacy/luna/AutoTradePatcher.java
java -cp "build/tools;$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" MakeAutoTradeCompileJar `
  inputs/luna_final-golden-baseline.jar build/generated/.autotrade-compile.jar symbols/autotrade-symbols.properties
javac -encoding UTF-8 --release 7 -cp "build/generated/.autotrade-compile.jar;$dependencyDir\microemulator.jar;build/semantic" `
  -d build/generated build/generated/HSOAutoTradeSupport.java
java -cp "build/tools;$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" `
  -Dluna.generated.dir=build/generated `
  -Dluna.support.source=build/generated/HSOAutoTradeSupport.java `
  -Dluna.support.template=tools/legacy/luna/HSOAutoTradeSupport.java.in `
  -Dluna.runtime.source=tools/legacy/luna/HSOSKYRuntimeSupport.java `
  -Dluna.symbols=symbols/autotrade-symbols.properties `
  AutoTradePatcher inputs/luna_final-golden-baseline.jar $autoTradeInput
java -Xverify:all -cp "build/tools;$dependencyDir\asm-9.7.jar;$dependencyDir\asm-tree-9.7.jar" `
  -Dluna.symbols=symbols/autotrade-symbols.properties `
  luna.tools.XpPopupPatcher $xpPopupInput $outputJar

java -Xverify:all -cp "build/tools;microemulator.jar" `
  luna.tools.LunaJarVerifier $outputJar --load-classes `
  --compare-resources inputs/luna_final-golden-baseline.jar `
  --allow-resource x1/mfont/tahoma_14b_green.png `
  --allow-resource mfont/tahoma_14b --write-manifest $manifestPath
javac -encoding UTF-8 --release 7 -d build/test tools/legacy/luna/TestAutoTradeRoute.java
java -Xverify:all -cp "build/test;$dependencyDir\microemulator.jar" `
  TestAutoTradeRoute $outputJar "$dependencyDir\microemulator.jar"
# Optional manual GUI smoke test (run separately; it stays open until closed):
# java -cp "microemulator.jar;$outputJar" org.microemu.app.Main $outputJar
```

The diagnostic commands above leave the JAR in `build/releases/`. The canonical
script additionally publishes it to `../jar/` after verification and archives
the previous release. Use the script for releases.

The resulting manifest is the release record. It must show one hook facade,
23 compiled semantic classes, `legacy.bridge=true`, maximum class major 51,
730 resources (728 baseline resources plus the two generated XP-font
resources), and a class-load pass.
For a release, copy the concrete output name, hash, manifest path, and tested
status into the version matrix and update `Last verified` only after the
required checks pass. Never claim live server validation from packaging alone.

## 9. Version matrix

| JAR input | Manifest/version | Class version | Symbol catalog | Feature set | JAR output | Status | Hash/manifest |
|---|---|---|---|---|---|---|---|
| `inputs/luna_final-golden-baseline.jar` | HSOLUNA / 4.0.2 | game 47, support 51 | `symbols/luna-4.0.2.properties` | AutoTrade, N4, revive, ghost, UI hooks | N/A (golden) | current rollback baseline | SHA-256 `06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F`; `build/luna-4.0.2.golden.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47; 30 support/semantic classes at 51 | same catalog + hook spec | AutoTrade, N4, revive, ghost, semantic controllers/policy bridge | `luna-4.0.2-refactor-source.ps1.jar` (not retained) | last verified source build | SHA-256 `930999393F0544B9C01744A995A61B96B64AA21B9C8628839A14ED337FD7B027`; `build/luna-4.0.2-refactor-source.ps1.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47; 30 support/semantic classes at 51 | same catalog + hook spec | AutoTrade, N4, revive, ghost, combat recovery + target-ready verify | `luna-4.0.2-autoattack-recovery-v2.jar` | verified recovery build | see `build/luna-4.0.2-autoattack-recovery-v2.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47; 30 support/semantic classes at 51 | same catalog + hook spec | combat recovery + target-ready verify + target-lost watchdog | `luna-4.0.2-autoattack-recovery-v3.jar` | verified recovery build | see `build/luna-4.0.2-autoattack-recovery-v3.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47; 30 support/semantic classes at 51 | same catalog + hook spec | recovery + target watchdog + debounced native-drift + direct target reacquire | `luna-4.0.2-autoattack-recovery-v5.jar` | historical verified recovery build | see `build/luna-4.0.2-autoattack-recovery-v5.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols | AutoTrade, N4, revive, no GhostMod menu/captcha, persistent horse first-open/login recovery, single Auto Đi Buôn row, XP text bold/2x with 1.5x lifetime and slower fall | `luna-4.0.2-xp-green-2x-v16.jar` | historical verified release | SHA-256 `464D0FE44A7A3151147C46F32E8B440D76F4D36E3A8813EBEF0EC2E59D8D9B88`; `build/luna-4.0.2-xp-green-2x-v16.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX title/open fields | all v16 features + suppress native login menu titled `Tự động` for 5s after login/world entry | `luna-4.0.2-hide-auto-popup-v1.jar` | historical verified release | SHA-256 `2394002E487D541F0CB53ADD20E0BFFE110B585792D8C9E7017295B0B42CD513`; `build/luna-4.0.2-hide-auto-popup-v1.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX title/open fields | all v16 features + close native login menu titled `Tự động` on every frame while open | `luna-4.0.2-hide-auto-popup-v2.jar` | historical verified release | SHA-256 `015A96FD9CBDFF0CCC36C568AE09D5E9C8321BA5F3DFAACB08B6308D13EF6E9C`; `build/luna-4.0.2-hide-auto-popup-v2.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX title/open fields | all v16 features + block native menu titled `Tự động` directly at `MENUBOX.show`, with frame fallback | `luna-4.0.2-hide-auto-popup-v3.jar` | historical verified release | SHA-256 `5D701CBF552976BE5F969D1179C00052BA6AA35B0DE1000D96DF4EED64901B2C`; `build/luna-4.0.2-hide-auto-popup-v3.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + login key hook | all v16 features + one index-5/ENTER press 500ms after login/world entry; no direct menu suppression | `luna-4.0.2-login-enter-v4.jar` | historical verified release | SHA-256 `B09793998B4D6E912B567CDE6CBE59C1D0E495FD4723E9889455050D2F33F483`; `build/luna-4.0.2-login-enter-v4.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v16 features + block native `Tự động` at `MENUBOX.show`, with one index-5/ENTER fallback after 500ms | `luna-4.0.2-hide-auto-popup-v5.jar` | historical verified release | SHA-256 `AAC955D06EA97DFAA061C57F0857869B62899FB31FBF0CB125677B76A2CC5245`; `build/luna-4.0.2-hide-auto-popup-v5.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v16 features + block native `Tự động` at `MENUBOX.show`, with one index-5/ENTER fallback after 500ms | `luna-4.0.2-hide-auto-popup-v6.jar` | historical verified release | SHA-256 `B6AB126C6DF42BBE2E237EC8B3E03FA027C6791A9597A2CCA3F2752D1205B452`; `build/luna-4.0.2-hide-auto-popup-v6.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v6 features + menu mode selection `Auto chỉ quái` / `Auto quái + PK`; default remains mode 7 (monsters only), selection preserves chat 7/6 behavior | `luna-4.0.2-auto-attack-modes-v1.jar` | historical verified release | SHA-256 `18D62C2611741BE382EB7B715B0AEC45332354428F6A5213AE548047EBB1E6F4`; `build/luna-4.0.2-auto-attack-modes-v1.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v6 features + menu mode selection `Auto chỉ quái` / `Auto quái + PK`; default remains mode 7 (monsters only), selection preserves chat 7/6 behavior; verifier also checks bridge calls | `luna-4.0.2-auto-attack-modes-v2.jar` | historical verified release | SHA-256 `8D7A2A1E3ACFC536EC801899C5B748A986042EED5A4DF5E80FE5DCD06A59BB59`; `build/luna-4.0.2-auto-attack-modes-v2.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v2 features + XP popup falls another 1/3 slower than v2 and survives 50% longer than v2 (2.25x baseline lifetime) | `luna-4.0.2-xp-slower-v1.jar` | current verified release | SHA-256 `296853E60ADE981FF466EE5B5390DF415F5C14687CF53E64D2F63F274E71029A`; `build/luna-4.0.2-xp-slower-v1.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all previous features + bounded one-shot white-horse mount after world settle; native 2.5s horse retry gate removed; transient item failure retries at most twice; exact Auto Horse failure modals suppressed and fed back into the bounded state machine; connection-wait gate before first request | `luna-4.0.2-horse-one-shot-v3.jar` | current verified release | SHA-256 `7801FFE09D1936362D1ED6652F97CFC12FDFBB1D379B702A0C152DC0D52787E0`; `build/luna-4.0.2-horse-one-shot-v3.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v3 features + recognize server reconnect cooldown and issue one delayed login retry after 10.5s, with no per-frame spam | `luna-4.0.2-login-reconnect-v1.jar` | historical verified release | SHA-256 `EDB8633D1AAAD26A609951DD1E1A88C343822C0BA88DE98F2578AF31D3599F95`; `build/luna-4.0.2-login-reconnect-v1.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v1 features + recover from the logo/loading screen by returning to login before the delayed retry | `luna-4.0.2-login-reconnect-v2.jar` | historical verified release | SHA-256 `B8DC77F12BBF62AEDE56FD54BF7613C2A1964C886DBC77CC1422A5EC945C78E7`; `build/luna-4.0.2-login-reconnect-v2.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | all v2 features + strict game-world validation, disconnect-triggered retry arming, no per-frame screen reset while waiting, and a guarded 30-second loading watchdog that cannot arm on normal in-game screens | `luna-4.0.2-login-reconnect-v4.jar` | current verified release | SHA-256 `76530483A97E52E782B7E7BBE54706D064EA2920283899CF2D8350244AF92BEA`; `build/luna-4.0.2-login-reconnect-v4.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v4 reconnect flow plus direct native login trigger after the 10s cooldown; server-side same-session opcode-1 retry acceptance while no character is active | `luna-4.0.2-login-reconnect-v5.jar` | latest verified reconnect release | SHA-256 `B1469D7FC790A7AB773E49F1BB4F2D40F9B55F945557B3E3D3BDA86614FBCF9D`; `build/luna-4.0.2-login-reconnect-v5.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | V7 stable release with custom reconnect gated behind Auto Login selection; native auto menu restored and one-shot ENTER cleanup retained | `luna_final.jar` | active stable release | SHA-256 `6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`; `build/luna_final.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v7 menu restoration plus UTF-8/comma-decimal cooldown parsing anchored to the received notice, retaining one-shot ENTER cleanup | `luna-4.0.2-login-reconnect-v8-menu-restored-robust-cooldown.jar` | latest verified menu/reconnect release | SHA-256 `06BD14B5172A749D35E15798044CB39EEE16A17EC1BFC5FE50C37BECBC97FF5C`; `build/luna-4.0.2-login-reconnect-v8-menu-restored-robust-cooldown.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v8 features plus allowing an armed retry to dismiss a stuck connecting/loading dialog and proceed to native login | `luna-4.0.2-login-reconnect-v9-menu-restored-retry-dialog-fix.jar` | latest verified menu/reconnect release | SHA-256 `3470A747792011FDCC674A89517B7167A6F606122DB4446D7B244D93515D9BDB`; `build/luna-4.0.2-login-reconnect-v9-menu-restored-retry-dialog-fix.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v9 features plus forcing a pending reconnect through a stale dialog object in the same frame before native login | `luna-4.0.2-login-reconnect-v10-menu-restored-retry-force-login.jar` | latest verified menu/reconnect release | SHA-256 `2BD12BAE65C8F46A272335618501BFDE8F49C04539A48EBD2D62EAB474E2D600`; `build/luna-4.0.2-login-reconnect-v10-menu-restored-retry-force-login.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v10 features plus ignoring stale native disconnect timestamps older than one minute when scheduling a new retry | `luna-4.0.2-login-reconnect-v11-menu-restored-retry-robust.jar` | latest verified menu/reconnect release | SHA-256 `5FB8E985D6E535866D1B7B1723FFFB3E02726A76740AABABBFF372AA165FC7FA`; `build/luna-4.0.2-login-reconnect-v11-menu-restored-retry-robust.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v11 features plus strict native loading guard: native `onCanvasUpdate()` no longer clears disconnect state from a stale player object while the logo/loading screen is active; retry can observe the disconnect until the real game screen/map is ready | `luna-4.0.2-login-reconnect-v12-menu-restored-strict-world.jar` | latest verified menu/reconnect release | SHA-256 `91F4C20E800880304F601FB28AA36B41E7BF24FF1E487D1ED54378AA6891B656`; `build/luna-4.0.2-login-reconnect-v12-menu-restored-strict-world.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v12 features plus rebuilding the original native socket/thread transport immediately before a delayed retry when `isDisconnected` is true, then submitting the login packet | `luna-4.0.2-login-reconnect-v13-menu-restored-transport-restart.jar` | latest verified menu/reconnect release | SHA-256 `3B92652583EC93FBC1C434ADBF427288F9E254A3A60A39E5F50856240CA34A19`; `build/luna-4.0.2-login-reconnect-v13-menu-restored-transport-restart.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v13 features plus always rebuilding the original socket/thread transport on the armed delayed-retry path, covering the case where native clears `isDisconnected` before the 10-second cooldown expires | `luna-4.0.2-login-reconnect-v14-menu-restored-transport-restart-unconditional.jar` | latest verified menu/reconnect release | SHA-256 `E6035B7C5736142349C1D41F21A0C96C1B8E76D976916928E54DC014321CFBB7`; `build/luna-4.0.2-login-reconnect-v14-menu-restored-transport-restart-unconditional.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v14 features refined to preserve the existing TCP session for the server's cooldown notice; transport rebuild is reserved for disconnect/watchdog fallback | `luna-4.0.2-login-reconnect-v15-menu-restored-same-session-fallback.jar` | historical verified release | SHA-256 `E80061D1FF95CA2E86711F62A61632A32FD834434BE561E1F163E2FF64CEE5B0`; `build/luna-4.0.2-login-reconnect-v15-menu-restored-same-session-fallback.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v15 behavior plus direct observation of incoming opcode 2; cross-thread retry flags are volatile; native `Tự động` menu remains available and one-shot ENTER cleanup remains active | `luna-4.0.2-login-reconnect-v17-network-opcode2-volatile.jar` | historical verified release | SHA-256 `A6AD97AC8968C173E3D15E7FECDA6C0976122791AA2619A614B284859EC64C8B`; `build/luna-4.0.2-login-reconnect-v17-network-opcode2-volatile.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v17 behavior plus handshake-gated transport fallback, exact capture/replay of the last plaintext opcode-1 packet on a live transport, native `Tự động` menu preservation, one-shot ENTER and next-frame key release | `luna-4.0.2-login-reconnect-v22-captured-packet-replay.jar` | latest verified source build (server test pending) | SHA-256 `E21CDA6BA86A90DB27B73524FDBDCCBF1D4143C0073BD69FBE8F55AD47426FC5`; `build/luna-4.0.2-login-reconnect-v22-captured-packet-replay.manifest` |

| `luna_final.jar` | HSOLUNA / 4.0.2 | game 47, 186 classes at major 47 and 30 at major 51 | same catalog + hook spec + XP popup symbols + MENUBOX show/title fields + login key hook | v22 plus inbound opcode-2 fallback, 5s same-session retry escalation to fresh socket/handshake, and preserving one-shot ENTER state across native menu overlays | `luna-4.0.2-login-reconnect-v25-opcode-fallback-menu-overlay.jar` | latest verified source build (server test pending) | SHA-256 `60589F19BB2493D6A4817E05412B1D9495EE309CF7025F62E88CA6F8B5C56E33`; `build/luna-4.0.2-login-reconnect-v25-opcode-fallback-menu-overlay.manifest` |
| `luna_final.jar` | HSO CHILD / 4.0.2 | unchanged from input (216 classes) | no symbol changes | existing Luna features; server IPs repointed to 221.121.0.242; default server label changed to HSO CHILD | `build/luna-4.0.2-hso-child.jar` | derived endpoint variant; packaging inspected, connection untested (Luna port unchanged) | SHA-256 `DC75576D3E9F4AFFB80C0E0649B447EA9AF025DEF4DA747B192354E188B8FAEA`; `build/luna-4.0.2-hso-child.manifest` |
| `luna_final.jar` | HSOLUNA / 4.0.2 | unchanged from input (216 classes) | same catalog; no symbol changes | native pickup filters retained; local distance gate removed for drops in the current map list | `build/luna-4.0.2-full-map-pickup.jar` | derived full-map pickup; client gate structurally patched; server acceptance not tested | SHA-256 `DA57FDC4A469A1D52AEEB073C9E2A444E5CFBE94B9A32C511CFB0D5EA11B4081`; `build/luna-4.0.2-full-map-pickup.manifest` |
| `build/luna-4.0.2-hso-child.jar` | HSO CHILD / 4.0.2 | game 47; 216 classes | same catalog; no symbol changes | full-map pickup + HSO CHILD endpoint 221.121.0.242; inherited cast/auto-combat request gate is 100ms | `inputs/luna-4.0.2-hso-child-full-map-pickup.jar` | previous per-server release; JAR structure verified; basic-hit rate and server acceptance not tested | SHA-256 `A4B012F30D6E0D1FE10EF25A6D81BE9E0B380E095BBC778AFF0C5CDCF6DACA64`; `build/luna-4.0.2-hso-child-full-map-pickup.manifest` |
| `inputs/luna-4.0.2-hso-child-full-map-pickup.jar` | HSO CHILD / 4.0.2 | game 47; 216 classes | same catalog; no symbol changes | movement cadence 5/8 ticks (x1.25) changed to 8/8 ticks (x2); other entries unchanged | `client/jar/hsochild_final.jar` | current per-server release; ZIP integrity, one-byte diff, manifest and bytecode inspection passed; live movement untested | SHA-256 `D7FC5548F9D0ED9A09ACA443D218857616BAAE8EBD3AF30BAD185FE399D26767`; `build/hsochild-run-speed-2x-20260924.manifest` |

Stable release artifact:
`luna_final.jar`, copied from
`luna-4.0.2-login-reconnect-v7-menu-restored-default.jar`, SHA-256
`6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`.
The V7 release keeps the native `Tự động` menu available, preserves the
one-shot ENTER cleanup after login/world entry, and gates custom reconnect
behind Auto Login selection; manual login does not trigger the 10-second
watchdog. The failed V8–V49 reconnect
experiments are no longer active release artifacts; their source golden is
preserved separately as `luna_final-golden-baseline.jar`.

### Feature map

| Feature | Semantic owner | Legacy boundary |
|---|---|---|
| Auto Đi Buôn | `AutoTradeController` | `HSOAutoTradeSupport` hook façade |
| Auto Ngã 4 | `N4Controller` | movement/combat hook façade |
| Revive | `ReviveController` | revive hook/bridge |
| Auto horse persistence | `PersistentHorseController` | `HSOSKYPetFeedSupport` RMS/mount bridge |
| Menu/HUD/notice | `src/luna/ui` | menu and paint hooks |
| XP floating text | native popup type 1, verified by packet 30 symbols; current profile is 2.25x baseline lifetime and 4/9 baseline fall profile | `tools/luna/XpPopupPatcher`, generated 2x font resources |
| Login/native “Tự động” popup | native menu preserved + one-shot fallback | generated `postLoginEnterTick()` -> `pressOk()` after 500ms |
| Auto attack target mode | `HSOSKYPetFeedSupport` mode state; default `autoPlayersEnabled=false` | `ENGINE.update` menu rows -> generated bridge actions; `Auto chỉ quái` = chat 7, `Auto quái + PK` = chat 6 |
| HSO CHILD movement cadence | native `ENTITY` movement update | `tools/luna/patch-run-speed.py` changes the inherited 5/8-tick mask to 8/8 ticks (x1.25 to x2) |

### Known invariants

Auto Horse persistence keeps the native menu/RMS checkbox, but native
`onCanvasUpdate()` is gated so it cannot re-use the mount item every 2.5s.
Lifecycle code waits for a settled player/map/inventory, sends one request per
world, retries only the explicit transient item-use error twice, and treats
the already-mounted notice as terminal success.

The player has one path writer. Map transitions and dialogs have settle
windows. A missing pet/entity observation is not a loss until the settle window
expires. Symbol lookup always includes owner, member, and descriptor. The main
mod menu has exactly one `Auto Đi Buôn` row: the patcher reuses the baseline
row and routes it to the semantic action instead of inserting a duplicate.
The login path preserves the native menu whose title is exactly `Tự động` at
`MENUBOX.show`. The generated bridge also sends one index-5/ENTER press 500ms
after the login screen hands control to the world. It must be armed once per
login/world transition and never run continuously in the frame loop. NPC menus,
the Luna mod menu, dialogs, and server notices remain untouched.
XP text is the packet-30 popup type 1 only; damage, HP, shop, and dialog text
must not use the XP font patch. Its generated `tahoma_14b` image/metrics are
the only newly allowed resources. The generated image is a compact indexed
sheet containing only the packet-30 characters (`<integer>xp`) so it does not
starve the J2ME render/network loop. The cross-class generated font field must
remain public for CLDC access. The current XP profile applies an additional 1.5x
slowdown over the previous release: total lifetime is 2.25x baseline and the
launch/gravity profile is approximately 4/9 of baseline. Only type-1 XP popup
objects use this profile.
The Auto attack mode menu must keep exactly the labels `Auto chỉ quái` and
`Auto quái + PK`; the initial mode is monsters-only (mode 7), and selecting a
mode enables Auto attack with the same state transition as chat 7/6. The
original `Auto Tự Đánh [ON/OFF]` toggle remains available for disabling it.
HSO CHILD's cast/auto-combat request gate is already 100ms, matching the
HSOSKYMOD sample. Its separate 250ms player-update checks also remain in the
sample and are not evidence of the basic Auto Tự Đánh cadence. Treat basic-hit
speed as unverified until accepted combat requests are observed on the server.
HSO CHILD movement uses every update tick after the speed patch; the prior
five-of-eight mask gave x1.25 and the new eight-of-eight mask gives x2 under
the inherited game-loop speed setting. Only one byte in the `ENTITY` class
changed. Movement and position acceptance on the live server remain untested.

## 10. Change log kỹ thuật

- 2026-09-24: verified HSO CHILD's existing movement mask 109 allows 5/8
  ticks (x1.25). Changed it to -1 for 8/8 ticks (x2) with
  `tools/luna/patch-run-speed.py`; verified a one-byte class change, unchanged
  manifest and other entries, 216 classes, ZIP integrity, and `javap` output.
  Backed up the prior release before publishing the new JAR. Live server
  movement remains untested.
- 2026-09-19: added semantic API/DTOs, fake clock, runtime, controllers,
  movement arbiter, path planner, UI state, and deterministic host fixtures.
- 2026-09-19: added versioned baseline metadata, exact symbol verifier,
  bridge-contract generator, JAR verifier, resource comparison, and manifest output.
- 2026-09-19: moved the obfuscated runtime body behind generated
  `LunaGeneratedLegacyHooks`; `HSOAutoTradeSupport` is now an ABI-only facade.
- 2026-09-19: wired route, portal graph, notice, haul policy, semantic classes,
  host tests, manifest generation, resource comparison, and class loading into
  the source build.
- 2026-09-19: added `tools/luna/build-source.ps1` as the repeatable canonical
  source-to-JAR and release-verification entry point.
- 2026-09-19: added lifecycle-gated combat recovery for revive/login/toggle,
  one-shot target reset/rearm, AutoTrade/N4 combat-state restoration, and the
  missing sender-opcode verifier helper.
- 2026-09-19: recovery verification now requires both native auto state and a
  valid target; a missing/invalid target gets one additional bounded clear/rearm
  attempt instead of being treated as recovered.
- 2026-09-19: added a 1.5s target-lost watchdog that only retries when the
  entity list still contains a valid candidate, avoiding retries on an empty map.
- 2026-09-19: debounced native-auto drift for 1.5s before entering recovery,
  so a transient animation/frame-level native flag does not interrupt skill cadence.
- 2026-09-19: added direct nearest-valid-target reacquisition from the live entity
  vector before falling back to bounded target-loss recovery.
- 2026-09-19: removed the Luna GhostMod menu, captcha hook, OCR/action path,
  and semantic `GhostController`; retained Revive as an independent feature.
- 2026-09-19: added `PersistentHorseController`, first-open and relogin host
  coverage, and legacy bridge re-arm calls to RMS/mount native support.
- 2026-09-19: fixed first-open/relogin horse timing: require the stable game
  screen without a dialog, keep the native 2500ms throttle, and never force an
  immediate item-use call while the horse menu or login transition is settling.
- 2026-09-19: strengthened `AutoTradePatcher` verification so the generated
  horse bridge must retain the game-screen, dialog, and native-throttle guards.
- 2026-09-19: added the native `hasCurrentDialog()` guard alongside the canvas
  dialog fields for first-open/relogin mount safety.
- 2026-09-19: build script now cleans generated compilation directories so a
  deleted source class cannot survive as a stale JAR entry.
- 2026-09-19: fixed the duplicate `Auto Đi Buôn` menu row by reusing the
  baseline row, routing it to action 9, and adding exact-one-row verifier
  coverage; hidden-menu mode removes the same native row.
- 2026-09-19: traced packet 30 to native popup type 1 and added
  `tools/luna/XpPopupPatcher`; XP now uses a generated 2x bold-green font,
  1.5x lifetime, and a 1.5x slower fall profile. The build allow-list covers
  only `x1/mfont/tahoma_14b_green.png` and `mfont/tahoma_14b`.
- 2026-09-19: fixed the XP popup CLDC runtime regression: the generated
  cross-class font field is public, the scaled sheet stays indexed and compact,
  and a direct MicroEmulator MIDP probe constructs/updates type-1 XP popups
  without `IllegalAccessError`; this keeps the receive loop alive after packet
  30.
- 2026-09-19: added owner/descriptor-verified `MENUBOX` title and open-state
  symbols and a login/world transition guard that dismisses only the native
  `Tự động` menu; the Luna mod menu, NPC menus, dialogs, and server notices are
  left untouched.
- 2026-09-19: widened the native-menu check from the login edge to every frame,
  fixing delayed popup creation while retaining exact-title filtering; added a
  packaged hook smoke test for close-vs-preserve behavior.
- 2026-09-19: kept the native `Tự động` menu available after the live test showed
  that direct `MENUBOX.show` suppression blocked the real menu; retained the
  one-shot index-5/ENTER fallback.
- 2026-09-19: added the native `CHỌN CHẾ ĐỘ` Auto attack rows `Auto chỉ quái`
  and `Auto quái + PK`; their generated bridge actions select mode 7/6 and
  enable Auto attack with the existing chat semantics, while the default stays
  monsters-only.
- 2026-09-19: applied the requested additional XP slowdown: the type-1 popup
  now falls at approximately 4/9 of the baseline profile and lives 2.25x the
  baseline (50% longer than the previous release); damage, HP, shop, and dialog
  popups remain unchanged.
- 2026-09-20: traced the reconnect countdown to incoming opcode 2. Its native
  internal presenter bypasses `CANVAS.showDialog`, so v15 never armed the retry
  from the visible cooldown. v16 hooks the packet handler immediately after
  `readUTF`, preserves the native presenter and same-session retry policy, and
  passed the full 9/9 source build including class loading/resource comparison.
- 2026-09-20: made the opcode-2 retry state `volatile` because the packet hook
  runs on the network reader thread while `tick()` consumes it on the canvas
  thread; v17 passed the same 9/9 build with 216 classes and 730 resources.
- 2026-09-20: v19 traced the remaining spin to the asynchronous native socket
  rebuild: the retry now observes outbound opcode 1, waits for reader handshake
  `-40` to enable encryption, and only then queues the login packet. The native
  `Tự động` menu is preserved; the one-shot ENTER cleanup remains unchanged.
- 2026-09-20: v19 passed the 9/9 source build, 216-class load, resource
  comparison, legacy route checks, and bytecode inspection of the handshake
  hook. Live-server confirmation is still pending.
- 2026-09-20: v22 added exact plaintext opcode-1 capture/replay for the
  same-transport cooldown path, restores the login UI fallback for rebuilt
  transports, and releases the synthetic ENTER key on the next frame so the
  native `Tự động` menu can be opened manually. It passed the same 9/9 checks;
  live-server confirmation is still pending.
- 2026-09-20: v25 added a reader-level opcode-2 fallback so the cooldown arms
  even when its UTF presenter differs, escalates a same-session retry to a fresh
  native socket/handshake after 5s without a world transition, and keeps the
  one-shot ENTER state across native menu overlay screen changes. It passed the
  same 9/9 checks; live-server confirmation is still pending.
- 2026-09-20: v34 identified the remaining root cause: the previous reconnect
  patch called `HSOSKYPetFeedSupport.onDisconnected()`, which only reset flags
  and never opened a socket. The patcher now finds the native
  `Connector.open` -> socket worker -> `Thread.start()` factory and calls it
  after the reset. Opcode-2 cooldown retries always use that fresh transport,
  wait for handshake `-40`, then replay opcode 1 -> character list -> enter
  world. The build passed all 9/9 checks, 216-class loading, and 730-resource
  comparison; live-server confirmation is still pending.
- 2026-09-20: v36 closed the sender race after the native socket rebuild: fresh
  opcode-1 retries use `triggerLogin()` instead of replaying the old sender,
  and opcode-13 enter-world replay resolves `SENDER.gI()` at send time while
  keeping the captured character id. It passed the same 9/9 build, class-load,
  resource, and route checks; live-server confirmation is still pending.
- 2026-09-20: v37 found that V36 opened the native socket factory twice: once
  from the patch and once inside native `triggerLogin()`. The patch now keeps
  opcode-2 cooldown retries on the existing server session, reserves native
  `triggerLogin()` for the single-socket fallback, and leaves `MENUBOX.show`
  byte-for-byte identical to the golden class. It passed all 9/9 checks,
  216-class loading, 730-resource comparison, route tests, and bytecode review;
  live-server confirmation is still pending.
- 2026-09-20: v38e removed the remaining dependency on opcode 2: a native
  connecting dialog on the login screen now arms the retry 10.5s after the
  real outbound opcode-1 request. Retries resolve `SENDER.gI()` at send time;
  the fallback waits for the actual handshake hook after one structural socket
  factory. The manual Auto-menu observer now releases a user ENTER edge on the
  next frame and the verifier rejects the old exact-title hide guard. It passed
  all 9/9 checks, class/resource loading, and route tests; live-server
  confirmation is still pending.
- 2026-09-20: v39 fixed the remaining watchdog surface condition: a logo/loading
  screen is now treated as a reconnect wait when the game screen is not active,
  so the 10.5-second retry cannot be skipped merely because `currentScreen` is
  not the login object. The manual-menu observer no longer depends on the
  localized `Tự động` title and only reacts to `keyPressed[5]`; it passed all
  9/9 checks, 216-class loading, 730-resource comparison, route tests, and
  bytecode review. Live-server confirmation is still pending.
- 2026-09-20: v40 removed the last screen-class assumption from the cooldown
  watchdog. After a real opcode-1 request, the retry is armed at 10.5s whenever
  strict game-world entry is not confirmed, including a spinner drawn by the
  game surface itself. It passed all 9/9 checks, 216-class loading,
  730-resource comparison, route tests, bytecode review, and an 8-second
  MicroEmulator startup smoke; live-server confirmation is still pending.
- 2026-09-20: v41 found that direct opcode-1 serialization still skipped the
  native login-screen state machine. The replay now calls the exact static
  `(String,String)V` helper reached by native `triggerLogin(4,0)` after the
  structural socket factory; serializer replay is retained only as fallback.
  It passed all 9/9 checks, class/resource loading, route tests, bytecode review,
  and an 8-second MicroEmulator startup smoke; live-server confirmation is
  still pending.
 - 2026-09-20: v42 made the server-independent watchdog path use a fresh native
   transport immediately when no cooldown opcode was observed, while keeping
   same-session replay only for a confirmed opcode-2 cooldown. It passed all
   9/9 checks, class/resource loading, route tests, bytecode review, and an
   8-second MicroEmulator startup smoke; live-server confirmation is still
   pending.
 - 2026-09-20: v43 added a bounded 5-second enter-world fallback for live server
   variants that do not expose the reference inbound character-list opcode.
   An actual outbound opcode-13 packet marks the native flow complete and
   avoids a duplicate replay. It passed all 9/9 checks, class/resource loading,
   route tests, bytecode review, and an 8-second MicroEmulator startup smoke;
   live-server confirmation is still pending.
 - 2026-09-20: v44 fixed a transport-start race where a fast native `-40`
   handshake callback could be overwritten by the post-factory initialization
   code, causing an indefinite spinner. It passed all 9/9 checks,
   class/resource loading, route tests, bytecode review, and an 8-second
   MicroEmulator startup smoke; live-server confirmation is still pending.
 - 2026-09-20: v45 accounted for live-server bootstrap frames observed after
   handshake and before login: fresh reconnects now wait 2 seconds after `-40`
   before invoking the native login helper, while early/stale opcode-13 traffic
   cannot mark the retry complete. The native menu observer also handles the
   key-held path so the post-login synthetic Enter does not close a manual
   Auto menu. It passed all 9/9 checks, class/resource loading, route tests,
   bytecode review, and an 8-second MicroEmulator startup smoke; live-server
   confirmation is still pending.
 - 2026-09-20: v46 changed confirmed opcode-2 cooldowns to use the same fresh
   native transport/handshake path as the generic watchdog fallback, because a
   live server may reject opcode 1 on the pre-logout reader. It also removed
   the `MENUBOX.show` observer entirely and verifies the native menu class is
   byte-identical to `luna_final.jar`; the one-shot ENTER cleanup remains in
   the canvas tick. It passed all 9/9 checks, class/resource loading, route
   tests, bytecode review, and an 8-second MicroEmulator startup smoke;
   live-server confirmation is still pending.
 - 2026-09-20: v47 found that the native factory alone only started a second
   reader; the old transport parent still owned open input/output streams.
   Reconnect now closes both native streams before starting the fresh factory,
   then waits for `-40` and the live-bootstrap settle gate. The native menu
   remains byte-identical to `luna_final.jar`. It passed all 9/9 checks,
   class/resource loading, route tests, bytecode review, and an 8-second
   MicroEmulator startup smoke; live-server confirmation is still pending.
 - 2026-09-20: v48 made the native-helper path observable: a void helper is no
   longer accepted as success unless the outbound opcode-1 hook confirms that
   the packet reached the current sender; otherwise the captured native
   serializer is used. It passed all 9/9 checks, class/resource loading, route
   tests, bytecode review, and an 8-second MicroEmulator startup smoke;
   live-server confirmation is still pending.
 - 2026-09-20: v49 moved opcode-1 retry confirmation from serializer entry to
   the normal-return edge. The entry hook still captures credentials and arms
   the watchdog, while the post-write hook commits success only after all
   native DataOutputStream writes complete; a closed old stream can no longer
   suppress the captured serializer fallback. It passed all 9/9 checks,
   class/resource loading, route tests, bytecode review, a direct ASM check
   showing both serializer returns have the completion hook, and an 8-second
   MicroEmulator startup smoke; live-server confirmation is still pending.
 - 2026-09-21: stable `luna_final.jar` was based on V7 and patched so the
   custom 10-second reconnect returns immediately unless
   `HSOSKYPetFeedSupport.selectedAutoLoginChar >= 0`. Manual login therefore
   keeps the native flow without opening a replacement transport. It passed
   class/resource loading, 216-class verification, and an 8-second
   MicroEmulator startup smoke.
- Last verified output: `luna-4.0.2-hide-auto-popup-v6.jar`, SHA-256
  `B6AB126C6DF42BBE2E237EC8B3E03FA027C6791A9597A2CCA3F2752D1205B452`;
  semantic/controller/route tests, first-open/relogin/settling horse tests,
  packet-30 XP patch verification, `-Xverify:all`, hook verification,
  resource comparison, class loading, an 8-second MicroEmulator startup smoke
  test, and a direct type-1 XP popup MicroEmulator probe passed.
- 2026-09-19: kept `luna_final.jar` and hook ABI unchanged; generated bridge and
  semantic source are now the maintained build path; the old staged wording
  below is historical only.
- Last verified output: `luna-4.0.2-horse-one-shot-v3.jar`, SHA-256
  `7801FFE09D1936362D1ED6652F97CFC12FDFBB1D379B702A0C152DC0D52787E0`;
  semantic/controller/route tests, bounded native horse gate verification,
  exact horse-dialog gate verification, menu action verification, symbol
  verification, generated bridge-call verification, XP patch verification,
  `-Xverify:all`, resource comparison, class loading, and legacy route
  verification passed.
- Last verified output: `luna-4.0.2-login-reconnect-v1.jar`, SHA-256
  `EDB8633D1AAAD26A609951DD1E1A88C343822C0BA88DE98F2578AF31D3599F95`;
  semantic/controller/route tests, cooldown-notice recognition, bounded login
  retry bridge generation, native horse/dialog gates, symbol verification,
  `-Xverify:all`, resource comparison, class loading, and legacy route
  verification passed.
- Last verified output: `luna-4.0.2-login-reconnect-v2.jar`, SHA-256
  `B8DC77F12BBF62AEDE56FD54BF7613C2A1964C886DBC77CC1422A5EC945C78E7`;
  semantic/controller/route tests, cooldown-notice recognition, delayed login
  retry, loading-screen recovery, native horse/dialog gates, `-Xverify:all`,
  resource comparison, class loading, and legacy route verification passed.
- Last verified output: `luna-4.0.2-login-reconnect-v3.jar`, SHA-256
  `BC397EA1502513FE128C3281C5A52678043D7D45E50447780C3CD050116A7278`;
  semantic/controller/route tests, UTF-8 cooldown recognition, strict
  world-state validation, disconnect-triggered retry arming, no per-frame
  loading reset, loading watchdog, `-Xverify:all`, resource comparison, class
  loading, and legacy route verification passed.
- Last verified output: `luna-4.0.2-login-reconnect-v4.jar`, SHA-256
  `76530483A97E52E782B7E7BBE54706D064EA2920283899CF2D8350244AF92BEA`;
  semantic/controller/route tests, UTF-8 cooldown recognition, strict
  world-state validation, disconnect-triggered retry arming, no per-frame
  loading reset, guarded loading watchdog, `-Xverify:all`, resource
  comparison, class loading, and legacy route verification passed.
