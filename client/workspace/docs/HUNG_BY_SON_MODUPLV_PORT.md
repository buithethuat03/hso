# Hung By Son — tính năng chuyển từ ModUpLv

**Nâng cấp bản đơn 2026-10-25:** mục Tấn công quái thường được bỏ khỏi menu, cơ chế đó thành mặc định
của đặc biệt; Auto quái + PK đổi thành Auto PK chỉ chọn người trong 365px (cập nhật 26/10). Nhịp/chiêu của vòng
mới tiếp tục dùng chung với nhánh quái toàn map. Xem [hành vi Auto PK](HUNG_BY_SON_AUTO_PK.md).
Các mô tả công tắc và tên bản copy bên dưới là bối cảnh lịch sử, không phải menu bản đơn nâng cấp.

Bản đơn (x1) và X3 từ 2026-10-24; ở X3 mỗi client có trạng thái riêng. Nguồn: decompile `ModUpLv.jar` (E282B0AA…). Hai client
dùng cùng giao thức; các opcode được đối chiếu từng hàm gửi:

| Việc | ModUpLv | Hung |
|---|---|---|
| Dùng vật phẩm | op 32 | op 32 |
| Chọn menu NPC | op -30 | op -30 |
| Trả lời hộp thoại | op -32 | op -32 |
| Lệnh 51 | op 51 | op 51 |

Map, ID NPC, tọa độ và nội dung hộp thoại lấy nguyên từ ModUpLv. Nhịp khung 28ms của
ModUpLv được giữ theo thời gian thực: 30 khung = 840ms, 50 khung = 1400ms, 5 khung = 140ms.
Mọi tùy chọn mặc định TẮT và không lưu qua lần mở client. Chưa thử trên server thật.
Mã nguồn: `tools/luna/HungBySonModUpLv.java.in` (lớp `HungBySonModUpLv`, `IModModule`).
Patch: `tools/luna/HungBySonModUpLvPatcher.java`.

## Auto đánh > Tấn công quái thường

- Dòng `Tấn công quái thường: [BẬT]/[TẮT]` (mã -117) nằm ngay sau "Tấn công đặc biệt".
- Đây là chế độ chọn mục tiêu của Tấn công đặc biệt, giống ModUpLv. Vì vậy bật quái
  thường sẽ bật luôn đặc biệt. Tắt quái thường thì đặc biệt vẫn bật. Tắt đặc biệt thì
  quái thường tạm ngưng.
- Khi đang bật, mỗi đòn đặc biệt (Auto đánh hoặc Farm) có mục tiêu chính là quái sẽ
  gửi danh sách:
  - mục tiêu chính native ở đầu;
  - 19 quái gần nhân vật nhất trên toàn map, trong tầm 20.000.000 (tức không giới hạn
    thực tế), tổng tối đa 20.
- Bỏ qua: quái chết, HP 0, Con Ma, quái bị loại, quái ngoài nhóm đã chọn ở
  "Tùy chọn tự đánh".
- Mục tiêu chính là người chơi (PK) hoặc NPC: giữ danh sách native.
- Chọn chiêu, hồi chiêu, MP, mốc 600ms và 30ms giữ như cũ, nên chiêu vẫn lấy từ thanh
  kỹ năng của Hung. ModUpLv lấy từ mọi chiêu đã học.

## Bản `Hung_By_Son_copy_special_skill.jar`: vòng đánh ModUpLv

Chỉ có trong file này (2026-10-24), x1/X3 ở trên giữ cách cũ. Nguồn: ModUpLv `K154.K067` (đánh),
`K154.K157` (nhặt đồ). Mã: `HungBySonModUpLv.frame()`, `tools/luna/HungBySonSkillStats.java.in`,
`src/luna/features/SkillCastStats.java`; build `tools/luna/build-hung-copy-special.ps1`.

- **Tấn công quái thường mặc định BẬT** (như ModUpLv). Bật **Tấn công đặc biệt** là tự đánh,
  không cần Auto đánh; nhân vật đứng yên, không cast/animation/di chuyển.
- Mỗi **28ms thời gian thực** (một khung ModUpLv), bất kể tốc độ game x0.8/x1/x3: vòng lặp canvas
  gọi một khung và rút ngắn giấc ngủ 16ms để thức đúng mốc 28ms.
- Mỗi khung, như ModUpLv:
  - xét chiêu đã học ID 20 → 0 (không theo thanh kỹ năng);
  - chiêu đầu tiên đủ MP, hết hồi chiêu và có số mục tiêu > 0 được gửi thẳng (op 9) tới
    **20 quái sống gần nhất toàn map** (gần trước);
  - chiêu 7/8/19/20 còn trong 600ms từ lần gửi trước thì **kết thúc khung**, không thử chiêu thấp hơn;
  - sau khi gửi: hồi chiêu theo cấp + cấp cộng thêm (hoặc override nhân vật), trừ MP ngay, mốc 600ms;
    tối đa một chiêu mỗi khung.
- Mục tiêu: quái (`cv` 1) HP > 0, không chết; bỏ Con Ma (để nhánh captcha của Farm), object đã
  xóa và nhóm quái bị loại ở "Tùy chọn tự đánh".
- **Nhặt đồ toàn map** khi Tấn công đặc biệt BẬT (kể cả khi tắt quái thường): mỗi vật phẩm loại
  3 (phẩm ≥ 3), 4, 5, 7 trong tầm 50000 được gửi op 20 một lần. Hàm khoảng cách native (chung với
  ModUpLv) tràn số sau ~46340 nên tầm này phủ cả map.
  **Từ x1 `2EB9258B…` (2026-10-26):** bỏ ngưỡng phẩm ≥ 3. Mỗi khung chạy vòng nhặt toàn map của
  Menu mod > Cài đặt cấu hình > Auto nhặt đồ: chỉ nhặt khi `Auto Nhặt: [BẬT]`; trang bị theo ô
  Trắng/Xanh/Vàng/Tím/Cam (phẩm 0–4, mã 20–49 quy về 0–4, phẩm khác luôn nhặt); loại 4/5/7 luôn nhặt
  khi BẬT. Vòng đó của mod chỉ chạy trong cập nhật Farm, mà Farm thoát sớm khi Auto đánh BẬT, Farm
  tắt, mê cung/phó bản hoặc đang về thành/bán rác — nên Tấn công đặc biệt tự gọi nó. Vòng nhặt gốc
  của game (ngưỡng trong cài đặt gốc, cũng toàn map) vẫn chạy độc lập; nó đánh dấu "đã xử lý" cả
  món nó bỏ qua, nên nếu quét trước thì vòng mod không nhặt lại món đó.
- Khi vòng đánh chạy: Auto đánh không chọn mục tiêu/tự đánh; bộ chọn đặc biệt của Auto đánh/Farm
  không gửi; Farm tại bãi đứng yên, coi lần gửi của vòng đánh là tiến triển (15s không gửi khi còn
  quái thì cơ chế kẹt của Farm được chạy). Lần gửi cũng đặt lại bộ đếm "không đánh" của Farm.
- Tắt Tấn công quái thường (giữ đặc biệt): quay về đặc biệt kiểu cũ qua Auto đánh/Farm (PK,
  chiến trường).

**Bảng (i) — tab "Chi tiết tung chiêu"**
- Thanh tab `Chi tiết EXP | Chi tiết tung chiêu` ở đầu hộp thoại EXP; chạm + thả trên tab để
  chuyển, Xác nhận vẫn đóng. Mỗi lần mở bắt đầu ở tab EXP; hai tab cùng độ rộng.
- Bảng: ID, Tên chiêu, Tung, Hụt, Trúng, dòng Tổng; chiêu ID cao trước; cập nhật mỗi 250ms khi mở.
- Mọi gói đánh (op 9/6) gửi khi Tấn công đặc biệt BẬT là một lần tung. Kết quả đòn của nhân vật
  khớp với lần tung cũ nhất chưa có kết quả của cùng chiêu: có mục tiêu mất máu → trúng; mọi mục
  tiêu sát thương 0 ("né đòn") hoặc 2 giây không có kết quả → hụt. Trúng = trúng / (trúng + hụt);
  `-` khi chưa có kết quả nào.
- Số liệu về 0 mỗi khi bật Tấn công đặc biệt (tắt rồi bật lại), không lưu RMS.

**Bản `Hung_By_Son_copy_special_gap.jar`: đo khoảng chờ X** (2026-10-24) — giống bản trên, thêm:
- Tab "Chi tiết tung chiêu" có dòng cuối: số lần **né** (có kết quả, mọi sát thương 0), **không trả
  lời** (2 giây), **trúng/phút** (từ lần gửi đầu tới lần gửi cuối của phiên).
- Tab thứ ba **"Khoảng chờ"**: mỗi lần tung đã có kết quả, theo thứ tự gửi, được xếp theo khoảng cách
  tới lần **trúng** gần nhất trước nó (ô 25ms, ≥1000ms gộp một ô):
  - mặc định nhóm theo **chiêu trước** (chiêu của lần trúng đó); chạm tiêu đề để đổi sang nhóm theo
    **chiêu sau** (chính chiêu được gửi);
  - mỗi dòng: Mẫu, **X** = ngưỡng tách tốt nhất "dưới X hụt / từ X trúng" (bằng nhau thì lấy X lớn hơn;
    `<=N` khi chưa có mẫu nào dưới N), tỉ lệ trúng dưới X và từ X;
  - chạm một dòng: bảng theo từng 100ms (chỉ các khoảng có mẫu); chạm tiêu đề để quay lại;
  - nút **Chế độ**: Tắt (như bản trên) → Tự đo → 450 → 500 → 550 → 600 → 650ms → Tắt. Khác Tắt thì
    vòng đánh chỉ gửi khi đã qua khoảng chờ kể từ lần gửi trước: Tự đo = X của chiêu vừa gửi + 20ms
    (600ms khi chiêu đó dưới 30 mẫu hoặc chưa có mẫu dưới X); mức cố định áp cho mọi chiêu. Mốc 600ms
    của chiêu 7/8/19/20 vẫn giữ; vòng 28ms nên thực tế trễ thêm 0–28ms;
  - nút **Xóa số đo**; số đo giữ qua các lần bật/tắt đặc biệt, mất khi tắt client; chế độ không lưu.
- **File log** `<user.home>/hso_logs/skill-gap-<epoch ms>.csv` (hoặc `-Dhso.log.dir`), mỗi lần chạy
  client một file, ghi mỗi giây; đường dẫn/lỗi hiện ở dòng cuối tab Khoảng chờ. Cột:
  `session,seq,skill,sent_ms,result(H/N/M),answer_ms,rtt_ms,targets_sent,nearest,targets_answered,damaged,`
  `prev_hit_skill,gap_prev_hit_ms,prev_sent_skill,gap_prev_sent_ms,pace,wait_ms`; dòng `#` ghi phiên,
  tên chiêu, đổi chế độ, xóa số đo. Cần runtime Java desktop/Android (KEmulator, MicroEmulator,
  J2ME Loader); điện thoại CLDC thuần không ghi được.

**Kết quả đo trên server thật (2026-10-24, log `skill-gap-1792823685366.csv`, Chiến binh)**
- Phiên Tắt 826s: 2730 lần tung, 1200 trúng (44,0%), 87,2 trúng/phút. Phiên Tự đo 643s: 993 lần
  tung, 868 trúng (87,4%), 81,0 trúng/phút. Mọi lần hụt đều là "không trả lời" (0 lần né): server
  bỏ qua im lặng chiêu bị từ chối; RTT thật khoảng 130ms (p90 ~200ms).
- Lỗi đo của bản gap: ghép câu trả lời với lần tung **cũ nhất** đang chờ nên khi lần trước bị từ
  chối, câu trả lời của lần sau bị gán cho lần trước (RTT giả ~1,1s, 704/2730 nhãn sai ở phiên Tắt).
  Tổng trúng/hụt theo chiêu vẫn đúng; X trong tab và Tự đo bị sai (ví dụ 1000ms sau Thiên thạch,
  500ms sau Hỏa kiếm chỉ trúng 75%). Phân tích lại offline bằng cách ghép với lần tung **mới nhất**.
- Luật quan sát được (khoảng cách tính theo giờ gửi, so với lần trúng trước):
  - sau chiêu 1–8 (và 14, 18): dưới ~450ms gần như luôn bị từ chối, 500ms khoảng một nửa, từ 550ms
    ~95–100% trúng — một "khóa" ~500ms phía server, như nhau cho mọi chiêu sau;
  - sau Trọng kiếm (0): không có khóa, chiêu kế tiếp gửi 0–50ms sau vẫn trúng (49/53);
  - Trọng kiếm cũng phải chờ khóa của chiêu trước; lần gửi bị từ chối không kéo dài khóa.
- Hệ quả: số đòn trúng bị chặn bởi khóa (~1 lần/550ms) chứ không bởi hồi chiêu; chèn Trọng kiếm
  ngay khi khóa hết rồi gửi chiêu khác ở khung kế tiếp cho thêm một đòn "miễn phí" mỗi giây.

**Bản `Hung_By_Son_copy_special_lock.jar`: đánh theo khóa server** (2026-10-24) — như bản gap, thêm:
- Ghép kết quả với lần tung **mới nhất** đang chờ của chiêu (ít nhất 30ms trước câu trả lời); các lần
  tung cũ hơn chưa có kết quả là hụt "không trả lời". Hạn chờ kết quả 700ms (trước 2 giây).
- Nút **Chế độ** (tab Khoảng chờ): Tắt (đúng ModUpLv) → Khóa 500 → 525 → 550 → 575ms; mặc định
  **Khóa 550ms**. Khi khóa: vòng đánh chỉ gửi khi đã qua khóa kể từ lần gửi gần nhất của một chiêu
  khác Trọng kiếm (0); Trọng kiếm không tạo khóa.
- Thứ tự khi khóa: **Trọng kiếm** (chèn vào ngay khi khóa hết, chiêu kế tiếp đi ở khung sau, 28ms),
  chiêu 9–18 (ID cao trước), **20/19/8/7**, rồi **1–6 theo cấp đã nâng** (cấp học + cấp cộng thêm,
  bằng nhau thì ID cao trước). Mốc 600ms của 7/8/19/20 giữ nguyên nhưng lần gửi Trọng kiếm không
  tính vào mốc.
- Nút **Tung lại** (mặc định Bật): lần tung mới nhất của một chiêu không có trả lời sau 700ms thì xóa
  hồi chiêu của chiêu đó để tung lại ở lượt kế tiếp; chỉ một lần cho tới khi chiêu đó có trả lời lại
  (phòng trường hợp server đang tính hồi chiêu). Log ghi `# retry skill N`.
- Chế độ và Tung lại không lưu qua lần mở client.

**Kết quả đo bản lock trên server thật (2026-10-24, `skill-gap-1792828067764.csv`)** — phiên dùng vòng
đánh mới: Tắt 81–85 trúng/phút (42%); Khóa 500ms 113–114 (67–69%); Khóa 550ms 130–134 (84–88%);
**Khóa 575ms 137 (92%)**. Chiêu gửi 28ms sau Trọng kiếm trúng ~90%; tỉ lệ dừng ~90% kể cả chờ
600ms (phần hụt còn lại không do nhịp). → `Hung_By_Son.jar` và `Hung_By_Son_x3.jar` mặc định
Khóa 575ms, không ghi file (dòng cuối tab Khoảng chờ hiện né / không trả lời / trúng/phút); X3 mỗi
client có bộ đếm, chế độ và khóa riêng.

Từ `Hung_By_Son.jar` `46C6C77F…` / x3 `17A4BEAA…` (2026-10-29), nút Chế độ đi tiếp sau 575ms:
Tắt → 500 → 525 → 550 → 575 → **600 → 650 → 700ms** → Tắt; mặc định vẫn Khóa 575ms. Ba mức mới
chưa có số đo server thật.

Từ `Hung_By_Son.jar` `56454EF0…` / x3 `CAE41379…` (2026-10-29) nút đi tiếp sau 700ms sang **Đáp 400 →
425 → 450ms** → Tắt; **mặc định Đáp 400ms** (không lưu, mỗi slot X3 riêng). Ở chế độ Đáp, khóa tính
từ lúc nhận trả lời của chiêu (khác Trọng kiếm/đòn 0) gửi gần nhất; chưa có trả lời thì 575ms từ lúc
gửi. Chỉ ở chế độ Đáp: chiêu kế tiếp gửi cùng nhịp ngay sau đòn 0 (gate 600ms của 7/8/19/20 vẫn kết thúc
nhịp) và vòng chạy ngay lúc khóa hết thay vì chờ nhịp 28ms. Khóa/Tắt giữ nguyên hành vi cũ.
Test server thật (Xạ thủ, ping ~190ms, log `skill-gap-1793259829414.csv`): Đáp 400 ~136 trúng/phút,
Khóa 575 130, Đáp 425 ~127–131, Đáp 450 124 — chênh lệch trong mức nhiễu của lượt 1,5–3 phút.
Chiêu gửi 0–40ms sau "Bắn thường" của Xạ thủ chỉ trúng ~72–80% (khác ~95%), Chiến binh không bị; chưa rõ
nguyên nhân. Dưới 400ms sau trả lời có hại khi ping thấp: giới hạn chính là ~550ms từ lúc gửi.

Từ `Hung_By_Son.jar` `879B7F69…` / x3 `08DE2070…` (2026-10-29) mặc định Đáp 400+; từ `C33DF6F2…` / x3
`C4F6B3A7…` **mặc định trở lại Khóa 575ms** theo yêu cầu, Đáp 400+ vẫn chọn được; nút đi tiếp
sau Đáp 450 sang **Auto → Đáp 400+** → Tắt. Đáp 400+ = nhịp Đáp 400ms, và chiêu khác gửi trong 400ms
sau đòn 0 đẩy mục tiêu đầu của đòn 0 xuống cuối danh sách quái (chỉ khi mục tiêu đó còn đứng đầu; Auto
PK/Xà nữ và buff không đổi). Nguyên nhân hụt sau "Bắn thường" là quái vừa bị đòn 0 giết: chiêu kế trúng
94% khi đổi so với 74% (test thật, Xạ thủ); tổng 130,4 vs 126,4 trúng/phút. Auto tự chỉnh θ (550+θ từ
lúc gửi / 400+θ từ trả lời) và độ chờ sau đòn 0 bằng thử A/B; thua Đáp 400 khi test thật (122 vs 129)
vì quá nhiễu, chỉ để thử nghiệm.

Từ `Hung_By_Son.jar` `42120E2A…` / x3 `728D8076…` (2026-10-29): **bỏ toàn bộ chế độ Đáp**, nút chỉ còn
Tắt → Khóa 500…700 → **Auto** (mặc định) → Tắt. Lý do: trên VPS ping thấp, Đáp 400+ gửi ~420ms sau lần
gửi trước (trong khóa ~500ms của server) và tụt còn ~80 trúng/phút. Auto mới: chờ max(G từ lúc gửi, 300ms
sau trả lời); G tự học 525–650ms (bắt đầu 575) bằng thử ngẫu nhiên G∓15 và so số trúng mỗi giây sau mỗi
40 mẫu mỗi bên, bước ±5ms; nếu sau một lần gửi theo G không có trả lời mà lần dự phòng 575ms lại trúng
(3 lần trong 12 mẫu) thì G tăng ngay 15ms. Gửi chiêu kế cùng nhịp sau đòn 0 và đẩy mục tiêu đòn 0 xuống
cuối danh sách (như Đáp 400+); Auto PK/Xà nữ không đổi mục tiêu. G học riêng từng client, mất khi tắt game.

**Chiêu buff (từ `Hung_By_Son.jar` `EA213F39…` / x3 `FC0565EB…`)** — chiêu loại 1 (buff, ví dụ Đạn
xuyên giáp, Sức mạnh chiến binh) không còn gửi như đòn đánh (op 9, server chỉ gây sát thương, không
áp buff) mà như client gốc: danh sách mục tiêu do hàm dựng mục tiêu native tạo (bản thân + đồng đội
trong tầm), gửi op 40. Câu trả lời buff của server (op 40, người tung là mình) tính là trúng trong
bảng; buff vẫn chiếm một lượt khóa như chiêu khác và đứng trước 7/8 trong thứ tự ưu tiên.

**Đối chiếu: `ModUpLv_skill_stats.jar`** — `ModUpLv.jar` gốc thêm đúng bộ đếm này (cùng
`SkillCastStats`, cùng định nghĩa trúng/hụt, đặt lại khi tấn công đặc biệt chuyển sang BẬT). Xem ở
menu "Tan cong / muc tieu" > "Chi tiết tung chiêu" (hộp thoại Ok, không có tab EXP). Logic đánh
của ModUpLv không đổi. Mã: `tools/luna/moduplv/`, `tools/luna/ModUpLvSkillStatsPatcher.java`.

## Cài Đặt Cấu Hình > Auto Lửa Thiêng

- Dòng `Auto Lửa Thiêng: [BẬT]/[TẮT]` (mã -96) nằm trước "Reset Cấu Hình".
- Khi bật, ghi nhớ map và khu hiện tại. Mỗi 1,4 giây, nếu đúng map/khu đó và có một
  auto đang chạy, thì:
  - map chưa có đống "Lửa thiêng": dùng vật phẩm "Lửa thiêng";
  - đống lửa dưới cấp 4: dùng "Bình dầu".
- "Auto đang chạy" gồm: auto native, Auto đánh hoặc Auto Farm. ModUpLv chỉ xét auto
  native của nó.

## MOD > Phó bản / Mê cung (mã -87)

### Bản đơn hiện hành: đứng chốt theo thông báo (2026-10-25)

Từ `Hung_By_Son.jar` `387334F9…`; X3 có từ `Hung_By_Son_x3.jar` `F7157198…` (đóng từ x1 `F07535B4…`, mỗi slot trạng thái riêng).
Menu Auto Phó Bản cũ (A, action -78) đã bỏ; tick/action của A được vô hiệu hóa.
Menu B, ba cách vào, trả lời phí và đường đi trước khi vào phó bản giữ nguyên.

Trong map 48, khi **Hoàn thành phó bản** bật:

| Hướng trong cùng thông báo server | Ô minimap đứng | Tọa độ giữa ô dùng để đi |
|---|---|---|
| Tây hoặc Bắc | (20,20) | (492,492) |
| Đông | (33,20) | (804,492) |
| Nam | (19,33) | (468,804) |
| Từ hai hướng khác nhau trở lên, ví dụ Bắc, Tây | (27,26) | (660,636) |

- Nghe thông báo mới có mẫu “Bầy quái vật sẽ xuất hiện ở cửa…”. Không đọc lại
  hàng đợi chữ cũ; reset khi ra map 48 hoặc tắt công tắc. Chưa có hướng thì đứng chờ.
- Gộp bản lặp trong 2 giây như ý tưởng Auto Ngã 4 của Luna. Thông báo hướng mới
  đổi chốt; thông báo cùng chốt không tạo thêm đường đi.
- Đi vào ô đã chọn rồi dùng Tấn công đặc biệt; không chọn quái để chạy đuổi.
  Tự đánh native bị giữ tắt, vòng đặc biệt 28ms/khóa 575ms và mục tiêu đánh hiện tại giữ nguyên.
- Nhặt thêm vật phẩm có tên đúng **Rương vàng** (không phân biệt hoa/thường, bỏ khoảng
  trắng hai đầu), chỉ trong map 48. Chỉ miễn ngưỡng tím+ cho món này; đồ vàng khác
  vẫn bị bỏ qua, các điều kiện loại vật phẩm/khoảng cách/đã gửi giữ nguyên.
  Từ x1 `2EB9258B…`: miễn bộ lọc menu Auto nhặt đồ (nhặt cả khi `Auto Nhặt: [TẮT]`); đồ khác theo menu.
- “Hoàn thành phó bản” vẫn không tự tắt Auto đánh khi người dùng tắt công tắc;
  hành vi này giữ như bản trước. Chưa thử server thật.

Kiểm chứng: `build/hung-dungeon-stationary-20261025-b/`; controller 40 kiểm tra,
native menu/packet/direction/loot 101, vòng đánh 85. Báo cáo so sánh trước sửa được
giữ làm lịch sử trong `HUNG_BY_SON_DUNGEON_COMPARISON.html`.

### Logic ban đầu (X3 và các bản trước thay đổi trên)

**Phó bản Ngã Tư Tử Thần >**
- Ba cách vào, bật/tắt độc lập như ModUpLv:
  - lượt free, đồng Horae;
  - ngọc;
  - cả hai.
- Phải bật thêm **Hoàn thành phó bản** thì mới chạy.
- Mỗi 840ms, nếu có ít nhất một cách vào được bật:
  - Ở map 1/34/69/93: đi tới Phó chỉ huy rồi gửi op -30 (-37, 0, 0).
  - Ở map 33/67: chỉ đi tới tọa độ.
  - Ngoài các làng đó và map 48: báo trên đầu nhân vật, sau 5 giây dùng vật phẩm 142
    (Bùa về làng).
- Trả lời hộp thoại bằng op -32 (0, 5, 1):
  - "…Ngã Tư Tử Thần một mình?" ở chế độ free hoặc cả hai;
  - "Phí: 5 ngọc" ở chế độ ngọc hoặc cả hai.
- Chế độ free gặp "Phí: 5 ngọc" thì tắt, không trả phí. Các thông báo sau tắt chế độ
  tương ứng và tắt Hoàn thành phó bản: "Để vào Ngã Tư Tử Thần cần…", "Cấp 30 mới
  được…", "Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần…". Từ bản `F07535B4…` (x1), mọi luật
  so khớp trên chữ đã bỏ dấu (dựng sẵn hay ghép rời), câu giới hạn 10 lần tắt mọi cách vào và
  Hoàn thành phó bản; 3 hộp thoại liên tiếp không trả lời được khi đang vào (ngoài map 48) cũng
  tắt tự động vào để không lặp hộp thoại.
- Trong map 48:
  - bật Auto đánh chỉ quái, kèm Tấn công đặc biệt và quái thường (tự dừng Farm như
    mọi lần bật Auto đánh);
  - đi tới con quái gần "Thiên thạch" nhất khi nó cách nhân vật hơn 150.

**Làng Phủ Sương >**
- `Tự động vào Làng Phủ Sương`, chạy mỗi 1,4 giây:
  - Map 135: đi tới "Serena", đứng yên trong 50 thì gửi op -30 (-104, 0, 1).
  - Map 1: tới "Mrs Oda", rồi gửi op -30 (-81, 0, 15) — dòng cuối `Làng phủ sương` của menu 16 dòng
    (x1 từ `206D2781…`, X3 từ `47739339…`; ModUpLv gốc gửi dòng 16, server bỏ qua). Khi Auto bán rác + Farm BẬT và chuyến bán
    chưa xong (trạng thái ≠ 0) thì chưa tới Oda.
  - Ở map 1/135, khi đích tìm đường (Farm sau bán đồ) là map router không có cạnh (125/126/127), tìm đường native
    tạm dừng để nhánh này dẫn đường (x1 từ `C9E5073D…`); vào map Farm thì Farm tự về tọa độ.
- Hộp thoại kết thúc bằng "sương" (hết vé) thì tự tắt.

**Auto Mê cung**
- Bản đơn từ 25/10/2026 chạy độc quyền: bật mê cung sẽ tắt Auto đánh, Farm,
  Tấn công đặc biệt, Tấn công quái thường, Auto Đi Buôn, Auto Cường Hóa,
  Lửa Thiêng, Phủ Sương và các công tắc Ngã Tư. Không khôi phục các chế độ này
  khi tắt mê cung; lệnh bật lại chúng bị chặn trong khi mê cung đang chạy.
- Đòn giữ cửa có quyền sở hữu riêng theo nhân vật/map/khu/mục tiêu. Farm OFF
  không còn xóa mục tiêu hoặc hủy đòn này. Bộ chọn quái/chiêu tự động native
  nhường quyền cho mê cung, nhưng animation và request native vẫn chạy.
- Đợi đòn đang tung hoàn tất trước khi thử chiêu tiếp theo. Tắt mê cung, đổi
  map/khu/nhân vật, chết hoặc mất kết nối sẽ nhả đòn và đường đi của mê cung;
  không thay các cài đặt Auto HP/MP, hồi sinh hoặc đăng nhập.
- Chạy mỗi 140ms theo đúng lộ trình của ModUpLv:
  - Làng 1/33/67/93: dùng vật phẩm 241 mỗi 1,5 giây.
  - Map 105–115: đi theo tọa độ cố định.
  - Map 106/109: đánh quái giữ cửa (ID 2/3/1) trong 150 bằng cách bật auto native và
    tung các chiêu sẵn sàng.
  - Map 109 và 112: xoay cửa.
  - Map 108: tới (501, 598) và gửi op -30 (-87, 0, 0).
- Hộp thoại "Xin chờ…" bị đóng.
- Hồi quy riêng: `fixtures/hung-by-son/HungBySonMazeExclusiveBinaryTest.java`.
  Chưa xác minh sát thương hoặc kết quả vượt mê cung trên server thật.

**Hoàn thành phó bản**: công tắc chính cho Ngã Tư Tử Thần (xem trên).

## Kiểm thử

- `fixtures/hung-by-son/HungBySonModUpLvBinaryTest.java` (62 kiểm tra) chạy trên JAR thật:
  - các menu native: bấm dòng thật, kiểm thứ tự và nhãn có dấu;
  - bộ dựng mục tiêu native: 3 mục tiêu khi tắt, 20 trên toàn map khi bật;
  - gói tin bắt ở hàng đợi gửi native cho Lửa thiêng, Ngã Tư, Phủ Sương, Mê cung;
  - hook hộp thoại.
- `fixtures/luna/NormalMonstersHostTest.java` (12): controller.
- Chưa thử trên server thật, nên chưa xác minh việc server chấp nhận danh sách 20 mục
  tiêu xa và các phản hồi NPC.
