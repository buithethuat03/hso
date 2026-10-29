# Client release record

Đường dẫn tính từ `client/workspace/`. Đây là nơi duy nhất cập nhật trạng thái release; lịch sử cũ không quyết định bản hiện hành.

## Artifact hiện hành

| Artifact | SHA-256 đọc từ file | Trạng thái |
|---|---|---|
| `../jar/luna_final.jar` | `F8998F3EC1570304DB02C7EDBD8AE844CCA90BE917B621B0F45A3DAA95C62CD2` | HSO CHIBI / 4.0.2, endpoint `103.77.173.232:19129` (IP của `chibi_up.jar`). Auto Ngã 4: bị đá ra làng thì chờ 35s rồi mới về; đi tới đúng toạ độ NPC Phó chỉ huy (chốt một lần mỗi lượt, tới gần thì đứng yên); quá 15s ở Làng Sói Trắng chưa vào phó bản thì dùng Bùa về làng (tối đa 3 lần). Host test 21 ca + `-Xverify:all` đạt; chưa thử emulator/server thật. Bản `DA57FDC4…` cũ nằm ở `../jar/luna_final_old.jar`. |
| `../jar/luna_final (1).jar` | `7632696AC82F120F03FDB54E882B6619A4A31D39F8BD182CABA5E4495FA16691` | Bản Luna người dùng chỉ định: login Hso + sửa dung lượng túi ở gói nhân vật opcode 3, ngăn đọc lệch pet/tail trước trạng thái vào game; số trang túi làm tròn lên, tối thiểu một. Native slice 350 kiểm tra, 194 class linkage, hồi quy và startup giả lập đạt; chưa xác nhận phiên đăng nhập server thật. |
| `../jar/hsochild_final.jar` | `B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671` | HSO CHILD / 4.0.2, endpoint `221.121.0.242`; giữ nhịp auto đập tối thiểu 500ms và tắt animation kết quả đập đồ/chế tạo. Không có logic tự động chế tạo lại đồ tinh tú. |
| `../jar/hsochild_update.jar` | `50BC808F4BEF0056F1A257F8C619E951A5A9F1D452F248AE4D8C7F785064734E` | Chỉ một `Server lậu` ở index 0, endpoint `221.121.0.242:19129`, key opcode 1; delay thực 300ms, tốc độ x1/x2/x3/x5/x10. Identity/data `HSOCHILD` / `HSOCHILD_`. Kiểm singleton/login, 94 menu clicks, 2502 timing, 242 class linkage và startup giả lập đạt; chưa thử server thật. |
| `../jar/hsochild_update_x3.jar` | `DD974E58206EA44A720D485DDAAE376A855B715E364BD62EAC3845EFADBFFC30` | X3 từ bản đơn chỉ một server / 300ms / x1/x2/x3/x5/x10 hiện hành. Identity `HSOCHILD X3` / `HSOCHILD_X3`, 3 virtual clients và data riêng. 751 class linkage, 1745 host checks, singleton/login đủ ba namespace, mở/chuyển 1→2→3→1 bằng giữ `*` trong giả lập đạt; chưa thử server thật. |
| `../jar/hsochild_final_x3.jar` | `1A7E7AAA6DA7A4197E83458F70DC54D14B793FAF1588B45FEA79AF1C608AA22D` | HSO CHILD X3 / 4.0.2; dùng launcher `MrQuyet.MrQuyet` từ mẫu x3 được người dùng chỉ định, quản lý ba client cùng một lần chạy. Chuyển 1 → 2 → 3 → 1 và giữ `*` đã kiểm trong giả lập; chưa thử server thật. |
| `../jar/chibi_final.jar` | `A78EFD2FFA52B179A0EF77CABFDB6C5294E91C0BB3BD87635F891EC44A1B1DEB` | HSO CHIBI / 4.0.2, `103.77.173.232:19129`; login Hso + opcode 16/65 short và dung lượng túi opcode 3 short. Tái hiện native slice cũ lỗi trước readiness, bản mới khớp Hso; làm tròn số trang túi lên, tối thiểu một. Native 350 kiểm tra, 217 class linkage, hồi quy và startup giả lập đạt; chưa xác nhận phiên server thật. |
| `../jar/chibi_final_x3.jar` | `7CE040DB02C303D843E41784B8C358BA707F0E1A6FAE1DEF4509F78B45235131` | X3 từ đúng Chibi Final `A78EFD2F…`, gồm các sửa login/túi-kho/self-init hiện hành. Suite `HSO CHIBI FINAL X3`, vendor `HSO_CHIBI_FINAL_X3`; ba client có RMS a/b/c riêng. 676 class linkage, 17.477 host checks, symbols/routes đủ ba slot, giữ `*` và chuyển 1→2→3→1 đạt trong giả lập. Chưa thử ba tài khoản server thật. |
| `../jar/max_final.jar` | `F2E4182D672DAC9F2BBD5992AF99584D1958AC37EC2230F8870B354E475C1239` | HSO MAX, từ đúng `chibi_final.jar`; server `HsoMax` / `sv.hsomax.com:19129`, login opcode 1 theo `HsoMaxMod.jar`. Đã sửa đọc mảng nhân vật byte/short ở opcode 3/5/15 làm lệch các mã trang bị; 15 ca native và toàn bộ body tail khớp bản mẫu. RMS `HSO_MAX_`; packet/class/symbol/hồi quy/giả lập đạt, chưa thử lại gameplay server thật. |
| `../jar/chibi_up.jar` | `34BCFBF74DB570954DFA563E931D98F84DB9796EE018FB75570EC2EC2472E500` | HSO CHIBI / `103.77.173.232:19129`; không đọc/ghi/khôi phục cache quái, không giữ xác/QCache, dùng vòng đời chết/dọn/hồi sinh native. Giữ login Hso.jar và RMS riêng `HSO_CHIBI_`; kiểm native, đăng nhập, hồi quy/giả lập đạt, chưa thử server thật. |
| `../jar/chibi_up_x3.jar` | `6153DB8885B3B74EB40F7D611FD4FD32933A5018224D4D2C35ADEE2920C47A40` | X3 từ đúng Chibi Up không cache; suite `HSO CHIBI X3` / `HSO_CHIBI_X3`, ba client với dữ liệu riêng và bảng EXP chung. 800 class linkage, 9.077 host checks, giữ `*`/chuyển 1→2→3→1 trong giả lập đạt; chưa thử server thật. |
| `../jar/bachho_final.jar` | `A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E` | Bạch Hổ / `103.77.166.204:19129`; đã rollback ba nâng cấp ngày 2026-10-04 theo yêu cầu vì bản mới không đạt. Khôi phục đúng bản auto diệt ma trước đó; giữ sửa ngựa, auto gate 400ms và native interval +300ms. |
| `../jar/Hung_By_Son.jar` | `42120E2AA98F26A1205DB7C179D8FE918666400E1CBF408BDE038BBFA6353320` | **Mặc định Auto (bản cải tiến)**: tự học khoảng chờ G từ lúc gửi (525–650ms, bắt đầu 575) + chặn 300ms sau trả lời + đổi mục tiêu sau đòn 0 + chốt chống quá ngắn. Đã bỏ Đáp 400/425/450/400+; còn Tắt, Khóa 500–700, Auto. Không ghi log. So với `C33DF6F2…` (backup) chỉ `SkillCastStats` (+ số dòng của `HungBySonModUpLv`). Mô phỏng ≥ Khóa 575 ở mọi mức ping; chưa thử server thật. |
| `../jar/Hung_test_attack.jar` | `D288CE898DAB97A6101E430E0BEFC1C7172FF01FD0DE8668A325E30E53BE8E3D` | Bản test v4 từ `Hung_By_Son.jar` `56454EF0…`: chế độ **Đáp 400+** (mặc định trong bản test) = Đáp 400 + chiêu đi ngay sau đòn 0 (≤400ms) đẩy mục tiêu của đòn 0 xuống cuối danh sách; log ghi "đổi MT". Vẫn có Đáp/Khóa/Auto. v3 `3C485DAD…` ở backup. Binary/UI giả lập/startup đạt; chưa thử server thật. |
| `../jar/Hung_By_Son_copy_special_skill.jar` | `5E2A225DB96E2D25BD9EE3489594A35C746C2E536923531F7E19DDE74EAE2187` | Endpoint mới từ x1 `193BA880…`: Tấn công đặc biệt chạy vòng đánh của ModUpLv độc lập (28ms thời gian thực, mọi chiêu đã học, 20 quái gần nhất toàn map, gửi thẳng, nhặt đồ toàn map); Tấn công quái thường mặc định BẬT. Bảng (i) có tab "Chi tiết tung chiêu" (tung/hụt/tỉ lệ trúng theo chiêu, đặt lại khi bật đặc biệt). 272 class; native/hồi quy/UI giả lập/startup đạt; chưa thử server thật. |
| `../jar/Hung_By_Son_copy_special_lock.jar` | `FBFBEE74ED5265B5907B88129F85B2648B34B09B72990BAAFBD32D46E5B92F81` | Endpoint mới (copy_special_skill/gap giữ nguyên): vòng đánh ModUpLv + chế độ **Khóa 550ms** mặc định (khóa server ~500ms đo thật sau mọi chiêu trừ Trọng kiếm), Trọng kiếm chèn trước, thứ tự 9–18 → 20/19/8/7 → 1–6 theo cấp; chiêu không có trả lời 700ms được tung lại một lần; ghép kết quả theo lần tung mới nhất. Tab Khoảng chờ: Tắt/Khóa 500–575ms, Tung lại Bật/Tắt; file log CSV như bản gap. 273 class; native/hồi quy/UI giả lập/startup đạt; chưa thử server thật. |
| `../jar/Hung_By_Son_copy_special_gap.jar` | `70D3FC06B656CA90449B30D9F69AB93E083C2526C1A0C12FA25FAED4978A6849` | Endpoint mới, cùng nguồn với copy_special_skill (giữ nguyên) + tab (i) "Khoảng chờ": X (khoảng chờ sau lần trúng trước) theo chiêu trước/chiêu sau, chi tiết 100ms, chế độ gửi Tắt/Tự đo/450–650ms (mặc định Tắt = như copy_special_skill); tab tung chiêu thêm né/không trả lời/trúng/phút; file log CSV mỗi lần tung tại `%USERPROFILE%\hso_logs\`. 273 class; native/hồi quy/UI giả lập/startup đạt; chưa thử server thật. |
| `../jar/ModUpLv_skill_stats.jar` | `76231D16A03FA8BC6D045FFC78D63DC18BBA542DEC86E2E6ACFFDCBA383A3A4E` | `ModUpLv.jar` (`E282B0AA…`, giữ nguyên) + menu "Tan cong / muc tieu" > "Chi tiết tung chiêu": cùng bộ đếm tung/hụt/trúng với bản copy special để so sánh; logic đánh của ModUpLv không đổi. 208 class liên kết, đếm qua serializer/handler thật và bảng vẽ trong giả lập đạt; chưa thử server thật. |
| `../jar/Hung_By_Son_x3.jar` | `728D80767773C5B028F4F9D6FA8ED39D26EC4D22429B745C4D04FB5E5E47393D` | X3 từ đúng x1 `42120E2A…`: mặc định Auto cải tiến ở cả ba client (G học riêng từng slot), không còn Đáp. Chỉ port + `SkillCastStats` ở 3 slot và manifest khác `C4F6B3A7…` (backup). 884 class; host 1.958, ModUpLv 46, Auto PK 69, cache 74, chiến trường 134, launcher 1→2→3→1 đạt; chưa thử server thật. |
| `../jar/Hung_By_Son_investigate.jar` | `0F92CFF49E6BE0E7A55FF8A48B9EE3460DBA451E2C4CD0F04D232FC62DDBEA8C` | Giữ quái đã xác nhận thành xác chết nhìn thấy, chặn xóa ở world/animation, dọn cờ xóa khi hồi sinh. Đọc HP độc lập với animation người đánh; mục tiêu không tiến triển nghỉ 3/6/12/24/30s. Giữ gate auto 700ms/request native, RMS/chọn nhóm/HUD. 217 cache, 36 recovery gồm 12 giờ mô phỏng native, 99/16 stale, timing/hồi quy/startup đạt; chưa thử bản này trên server thật. |
| `../jar/Hung_By_Son_updated.jar` | `EE2DF95933C939A82D5346AA093C9BAB38B4F9347FB7048041CA649C3A1D8694` | Gate auto >550ms thực; request >max(550ms, khoảng skill), đọc đồng hồ thực trực tiếp, không cộng 300ms. Giữ frame gửi khi chưa đủ thời gian để không bỏ chiêu do animation đi qua frame. 2.494 kiểm timing/request ở x1/x2/x3/x0.8/x0.9, hồi quy và startup đạt; chưa thử server thật. |
| `inputs/luna_final-golden-baseline.jar` | `06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F` | Khớp golden; bảo toàn byte-for-byte để rebuild/rollback. |

Đã rollback các thử nghiệm tự động khôi phục phiên chế tạo đồ tinh tú vì không hoạt động trên server thật. Bản hiện hành chỉ giữ việc tắt animation kết quả của màn hình đập đồ/chế tạo cùng các thay đổi ổn định có sẵn trước đó; không ghi hoặc phát lại opcode mở NPC/menu và không tự động xin lại công thức tinh tú.

Ngày 2026-09-27 đã tạo bản Chibi ban đầu từ `../jar/luna_final.jar`; hash Luna sau khi tạo giữ nguyên. Lần đó chỉ đổi endpoint/tên và kiểm archive/constants. Bản Chibi hiện hành đã có sửa tương thích đăng nhập bên dưới.

## Hung By Son x1/x3: Auto v2 default, Đáp modes removed (2026-10-29)

- User report: on a low-ping VPS Đáp 400+ fell to ~80 hits/min (it sent ~420ms after the previous
  send, inside the ~500ms server lock; the rejected send was followed by a 575ms fallback hit, so
  hits alternated). User request: replace Auto with an improved Auto that includes 400+, remove every
  Đáp option, default Auto, no file log, x1 and x3.
- `SkillCastStats` modes: Tắt, Khóa 500–700 (1–7), Auto (8, default). Auto v2: lock end =
  max(send + G, answer + 300ms), without an answer max(send + G, send + 575ms). G starts 575, range
  525–650, learned per client: each lock draws G ∓ 15; sends made within 40ms of a lock end that G
  set score their arm; with 40 samples per arm G moves ±5ms toward more hits per second (symmetric,
  no drift); windows where both arms mostly miss are skipped. After a G-set send got no answer, a
  575ms fallback send that hits is a strike; 3 strikes within 12 scored sends raise G by 15 at once.
  Same-frame send after đòn 0 with đòn 0's target moved behind (former Đáp 400+), early wake.
  Auto v1's delay after đòn 0 (F) is gone. The 300ms guard (not 350) only binds when the answer
  comes late; at 350 every high-ping cycle became RTT + 350 (simulation −6% vs Khóa 575).
- Simulation (server lock 500ms from receive, no dead-target effect, hits/min Khóa 575 → Auto):
  one-way 10±10ms 157 → 172 (G 525); 20+0..40ms 157 → 161 (G 545); 100+0..60ms 157 → 160;
  with 5% 250ms spikes 148 → 155 (low ping) and 148 → 154 (high ping).
- x1 `build/hung-auto2-20261029-a/Hung_By_Son.jar`, SHA-256
  `42120E2AA98F26A1205DB7C179D8FE918666400E1CBF408BDE038BBFA6353320`, from `C33DF6F2…`; port
  instructions unchanged (line numbers only). Checks: host 65 (end formula, low/high ping, no-lock →
  G 525, 560ms lock → strikes then settle 550–615, high ping/glitch leave G alone), CopySpecial 126
  (Auto chain, delayed-answer guard, fallback, early wake, 7's gate, rotation; Khóa 575 unrotated),
  ModUpLv 271, Special 52, Farm special 1,417, Auto PK 213 (battlefield + Auto), buff swap 83,
  cadence 787, linkage 94, battlefield 65, Khoảng chờ UI (Auto → Tắt), 12s startup.
- x3 (pin `AUTO2_SHA`) `build/hung-auto2-x3-20261029-a/Hung_By_Son_x3.jar`, SHA-256
  `728D80767773C5B028F4F9D6FA8ED39D26EC4D22429B745C4D04FB5E5E47393D`; host 1,958, ModUpLv X3 46
  (Auto default and lock per slot), Auto PK X3 69, cache 74, battlefield X3 134, launcher pass.
- Backups: `../jar/backup/Hung_By_Son-before-hung-auto2-20261029-a.jar` (`C33DF6F2…`),
  `../jar/backup/Hung_By_Son_x3-before-hung-auto2-x3-20261029-a.jar` (`C4F6B3A7…`).
  `Hung_test_attack.jar` (old v4 with Đáp modes and file log) left as is. Not played live.

## Hung By Son x1/x3: default back to Khóa 575ms (2026-10-29)

- User request: default Khóa 575ms like the old release; every other mode stays selectable.
  `SkillCastStats.PACE_DEFAULT` = 4 in the repo source. In Khóa modes the loop sends one skill per
  frame, never rotates targets and keeps the 28ms frame, so the default behaves like `46C6C77F…`
  (cadence trace identical: 149 packets in 60s at Khóa 575).
- x1 `build/hung-default575-20261029-a/Hung_By_Son.jar`, SHA-256
  `C33DF6F29581F3FB235C19FF1A4345B49CB6BA4BB4252E11D7BE54132501FDF9`, from `879B7F69…` (patcher input
  pin added); only `SkillCastStats` differs. Checks: host 78, CopySpecial 128, ModUpLv 271, Special
  52, Farm special 1,417, Auto PK 221, buff swap 83, cadence 787, linkage 94, battlefield 65,
  Khoảng chờ UI (Khóa 575 → 600), 12s startup.
- x3 (packer pin `DEFAULT_575_SHA`) `build/hung-default575-x3-20261029-a/Hung_By_Son_x3.jar`, SHA-256
  `C4F6B3A78DD6F1E8ADF7077715BEDE46EAB7AF4D8BEF22F304B8A33646B97303`; only root/b/c `SkillCastStats`
  and the manifest differ from `08DE2070…`. X3 host 1,958, ModUpLv X3 46 (Khóa 575 default per
  slot, Đáp 400+ answer lock in slot 3), Auto PK X3 69, cache 74, battlefield X3 134, launcher pass.
- Backups: `../jar/backup/Hung_By_Son-before-hung-default575-20261029-a.jar` (`879B7F69…`),
  `../jar/backup/Hung_By_Son_x3-before-hung-default575-x3-20261029-a.jar` (`08DE2070…`). Golden and
  other endpoints unchanged. Not played live.

## Hung By Son x1/x3: Đáp 400+ default, released (2026-10-29)

- User request after the live A/B (Đáp 400+ 130.4 vs Đáp 400 126.4 hits/min, skill after đòn 0
  94% vs 74%): release Đáp 400+ as the default for x1 and x3. `SkillCastStats.PACE_DEFAULT` = 12 in
  the repo source. Auto (mode 11) stays selectable although it lost its live test.
- x1: `build/hung-rotate400-20261029-a/Hung_By_Son.jar`, SHA-256
  `879B7F69E69B24C425D1D71D6D26D331FB3DCA3757B5BBB04DD290E85AF4D494`, `HungTestAttackPatcher`
  4-argument form (no file log) from `56454EF0…`; the port class compiled byte-identical to the
  live-tested v4 port. Checks: host 78, CopySpecial 128 ("Release build: no file log class",
  rotation [600, 601] → [601, 600]), ModUpLv 271, Special 52, Farm special 1,417, Auto PK 221
  (incl. battlefield pacing for Đáp 400/400+), buff swap 83, cadence 787, linkage 94 (287 classes),
  battlefield 65, Khoảng chờ UI (Đáp 400+ → Tắt), 12s startup.
- x3: packer pin `ROTATE_400_SHA`; `build/hung-rotate400-x3-20261029-a/Hung_By_Son_x3.jar`, SHA-256
  `08DE2070A36C5AF82551961823DC61EA494047A63668B76794C486C27D2593A4`; root/b/c port + counters and the
  manifest differ from `CAE41379…`. X3 host 1,958 (884 classes), ModUpLv X3 46 (Đáp 400+ per slot),
  Auto PK X3 69, crowded cache 74, battlefield X3 134, launcher 1 → 2 → 3 → 1 with empty stderr.
- Backups: `../jar/backup/Hung_By_Son-before-hung-rotate400-20261029-a.jar` (`56454EF0…`),
  `../jar/backup/Hung_By_Son_x3-before-hung-rotate400-x3-20261029-a.jar` (`CAE41379…`). Golden,
  `Hung_test_attack.jar` and other endpoints unchanged. The released files were not played live.

## Hung By Son: Mode chiến trường check after Đáp 400 / Đáp 400+ (2026-10-29)

- User asked whether the pacing update affects Mode chiến trường. Code: the changes live in
  `attackFrame` and `SkillCastStats` pacing; the PK branch still takes `nearestPlayers` (players, or
  only Xà nữ when the battlefield is ON), `pkLock`, kill tracking and the FIFO filter are untouched,
  and the Đáp 400+ rotation/`zeroSet` are guarded by `!pk`. PK only gets the Đáp pacing (same-frame
  send after đòn 0, early wake) that the released x1 already has.
- `HungBySonBattlefieldBinaryTest` 65 passes on the previous release `46C6C77F…`, the current x1
  `56454EF0…` and test v4 `D288CE89…` (catalog copies pinned to each hash; stage
  `build/hung-battlefield-check-20261029-a/`). `HungBySonBattlefieldX3HostTest` 134 passes on the
  released x3 `CAE41379…` (it had not been rerun when x3 was packed).
- New fixture section `HungBySonAutoPkBinaryTest.verifyBattlefieldPacing` (runs when
  `SkillCastStats.answerMode` exists): battlefield + Auto PK + Special, Đáp 400 and Đáp 400+, players
  and Xà nữ — đòn 0 and the next skill go in one frame with opcode 6 / 9, identical target order,
  monster ignored / Xà nữ only, next frame locked. 213 checks on x1 `56454EF0…`, 221 on test v4;
  the old release skips it (202). No live battlefield test.

## Hung test attack v4: Đáp 400+ target rotation (2026-10-29)

- Why: the skill sent right after đòn 0 hits ~75–80% vs 92–95% otherwise, and delaying it 20–100ms
  does not help (v3 log). Weak hint it follows a target death (78% when the nearest target changed
  afterwards vs 87%). Test: mode 12 "Đáp 400+" paces like Đáp 400ms, and the next non-free monster
  skill within 400ms of đòn 0 gets đòn 0's first target moved to the end of its list (only when it
  is still first; PK and buffs untouched). Such sends are logged with the pace "Đáp 400+ đổi MT".
- Port: `attackFrame` remembers đòn 0's first target id (`zeroSet`/`zeroTarget`/`zeroAt`) and calls
  the new `behind(et, short)`; `HungTestAttackPatcher` now adds exactly these listed members
  (`PORT_ADDITIONS`) besides swapping frame/frameSleep/attackFrame. `SkillCastStats` gains
  `PACE_ROTATE`, `rotateAfterFree()`, `markRotated()`; cycle … Auto → Đáp 400+ → Tắt. Repo source
  default stays 8; the stage compiles a copy with `PACE_DEFAULT = 12`.
- Build `build/hung-test-rotate-20261029-a/` from `56454EF0…`, file log on. Checks: host 78,
  CopySpecial 133 (real packets: Đáp 400+ sends [601, 600] after đòn 0 hit 600, Đáp 400 keeps
  [600, 601]), ModUpLv 271, Special 52, Farm special 1,417, Auto PK 202, buff swap 83, cadence 787,
  linkage 94, Khoảng chờ UI (stage copy: Đáp 400+ → Tắt), 12s startup. v3 backed up as
  `../jar/backup/Hung_test_attack-before-rotate-20261029-a.jar`. Not tested on the live server.
- Live result (log `skill-gap-1793264082315.csv`, Xạ thủ): Đáp 400+ 10.8 min (RTT p50 233ms) then
  Đáp 400 6.4 min (219ms). The skill right after đòn 0 hit 451/481 (94%) when rotated vs 224/304
  (74%) in Đáp 400 — the post-đòn-0 drop is a dead target. Without one 7s glitch (20 misses):
  Đáp 400+ 130.4 hits/min at 93.7%, 139 sends/min (non-free cycle mean 649ms) vs Đáp 400 126.4 at
  88.2%, 143 sends/min (629ms). At equal send rate Đáp 400+ ≈ 134 (+6%). Đòn 0 itself 95% vs 99%.

## Hung test attack v3: Auto pacing (2026-10-29)

- User request: an "Auto" option that tunes itself. `SkillCastStats` mode 11 `Auto` (cycle … Đáp
  450 → Auto → Tắt). Lock end = max(send + 550 + θ, answer + 400 + θ), 575ms fallback without an
  answer; after đòn 0 the next skill also waits the free delay F. Each lock picks a random arm θ ∓ 15,
  each đòn 0 an arm F ∓ 25. Sends made within 40ms of their lock end score their arm (hit, time since
  the previous non-free send); with 12 (θ) / 8 (F) samples per arm the knob steps toward the arm
  with more hits per second (θ −5/+10, F −10/+20; θ −80…150, F 0…250). If both arms mostly miss,
  θ rises by 20 when the 575ms fallback sends hit, F rises when other sends hit, otherwise the window
  is skipped as a glitch. Learned values live until the client closes (not stored).
- Port: in a Đáp/Auto mode the same-frame send after đòn 0 now also requires `paceReady`, so Auto's
  F holds it (Đáp unchanged: no F). Only `attackFrame` differs from the release port.
- Build: `build/hung-test-auto-20261029-a/`, input `56454EF0…` (patcher now accepts both released
  inputs), 6-argument file-log form. The stage compiles a copy of `SkillCastStats` with
  `PACE_DEFAULT = 11`; the repo source keeps `PACE_DEFAULT = 8` (Đáp 400ms) for releases.
- CSV: the pace column carries the arm (`Auto -`, `Auto +`, `Auto F-`, `Auto F+`, `Auto dự phòng`);
  each decision writes `# auto … : send …ms, answer …ms, after đòn 0 …ms`.
- Checks: host 75 (Auto end formula, F hold, three simulated servers: no lock → θ ≤ −40; 560ms
  lock → θ settles in −10…60; 60ms needed after đòn 0 → F 30…140), CopySpecial 129 (Auto holds
  the next skill F ms then sends in an early frame, both arms exercised), ModUpLv 271, Special 52,
  Farm special 1,417, Auto PK 202, buff swap 83, cadence 787, linkage 94, Khoảng chờ UI (stage copy
  expects Auto → Tắt), 12s startup. Not tested on the live server.
- Live result (log `skill-gap-1793262304115.csv`, Xạ thủ, RTT p50 220ms): Auto 122 hits/min over
  13.5 min (~132 for the first 4 min, then 110–125), Đáp 400 in the same run 129 (5.6 min); Đáp 400
  in `skill-gap-1793261001189.csv` 134 (10.8 min). Auto loses: (1) delaying the skill after đòn 0
  does not help (20–100ms after it still 77–84% hit vs 92–95% after a non-free skill), so F only
  costs time; (2) 12 samples per arm swing ~8% per miss against a ~5% time difference, so decisions
  are near-random and the asymmetric +10/−5 θ steps drift upward (θ ended at 575/425, non-free cycle
  p50 631ms vs 609ms). Kept as a test build only; releases stay on Đáp 400ms.

## Hung By Son x1/x3: Đáp 400ms default, released (2026-10-29)

- User request after the live test of `Hung_test_attack.jar`: default Đáp 400ms, no file log,
  replace the stable x1 and pack x3. `SkillCastStats.PACE_DEFAULT` = 8 (Đáp 400ms).
- x1: `build/hung-answer400-20261029-a/Hung_By_Son.jar`, SHA-256
  `56454EF0A5BB0812B72E949D96D7105F1318FBA9AFA915D7C3F21F5151F5E127`, built by
  `HungTestAttackPatcher` (4-argument form, no file log) from `46C6C77F…`. Same 1,066 entries;
  only `HungBySonModUpLv` (frame/frameSleep/attackFrame, identical to the tested build) and
  `SkillCastStats` differ. Checks: host 63, CopySpecial 117 ("Release build: no file log class"),
  ModUpLv 271, Special 52, Farm special 1,417, Auto PK 202, buff swap 83, cadence 787, linkage 94
  (287 classes), Khoảng chờ UI (Đáp 400 → 425), 12s emulator startup.
- x3: `HungBySonX3Packer` gains pin `ANSWER_400_SHA`; it first rebuilt the current x3 `17A4BEAA…`
  byte-identically from `46C6C77F…`, then packed
  `build/hung-answer400-x3-20261029-a/Hung_By_Son_x3.jar`, SHA-256
  `CAE413793F7B3CAB1CFBD232696FBED052DC09FD28DDBAF3EC295382BDC3A509`; differs only in
  root/b/c `HungBySonModUpLv` + `SkillCastStats` and the manifest. `HungBySonX3HostTest` 1,958
  (884 linked classes), `HungBySonModUpLvX3HostTest` 46 (Đáp 400 default and answer lock per slot),
  Auto PK X3 69, crowded cache 74, launcher 1 → 2 → 3 → 1 with empty stderr. The remaining
  `build-hung-pk-x3.ps1` suites (Battlefield/Farm spot/economy/rolling EXP/auto-attack X3) were
  not rerun; those classes are byte-identical.
- Backups: `../jar/backup/Hung_By_Son-before-hung-answer400-20261029-a.jar` (`46C6C77F…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-answer400-x3-20261029-a.jar` (`17A4BEAA…`).
  `Hung_test_attack.jar` (file-log test) and golden unchanged. The released files themselves were
  not played on the live server.

## Hung test attack: answer-anchored lock pacing, new endpoint (2026-10-29)

- Why: live log `skill-gap-1792828067764.csv` (2026-10-24) shows the ~500ms server lock after a
  non-0 skill also rejects Trọng kiếm (sent 0–499ms after an accepted non-0: 0/80 hits), so skill 0
  only fits at lock ends and fires every 2nd cycle. Khóa 575 measured 149 sends / 137 hits per
  minute (non-0 cycle ≈603ms: +14ms frame rounding, +28ms frame after Trọng kiếm every other
  cycle). Timed from the previous accepted cast's answer the hit rate reaches ~92% from 400ms
  (375ms: 71%), against ~600ms from the send; RTT p50 126ms, p95 205ms. Projection (not
  measured): Đáp 400/425/450 ≈ 155/149/143 hits per minute. After the lock ~10% of skills 1–6
  still go unanswered (7/8 ~1%), cause unknown.
- Change: `SkillCastStats` modes 8–10 = Đáp 400/425/450ms (cycle Tắt → Khóa 500…700 → Đáp
  400/425/450 → Tắt), default `PACE_DEFAULT` = 9 (Đáp 425ms) in the source. The lock runs from
  the answer to the newest non-free send; until it arrives, 575ms from the send. In Đáp modes only,
  `HungBySonModUpLv.attackFrame` continues after a Trọng kiếm send (same frame, the 600ms gate of
  7/8/19/20 still ends the frame), `frame()` runs early when the lock ends and `frameSleep`
  wakes for it. Khóa/Tắt modes are unchanged.
- Build: `tools/luna/HungTestAttackPatcher.java` (pinned input `46C6C77F…`) swaps only
  `frame()V`, `frameSleep(J)J`, `attackFrame` compiled from the template into the released
  `HungBySonModUpLv` (the template's other methods differ from later ASM patches, so the whole
  class is not replaced) and the recompiled `SkillCastStats`. Stage
  `build/hung-test-attack-20261029-a/`, 1,424,571 bytes; same 1,066 entries in order, exactly
  those two differ. Manifest/MIDlet identity is unchanged (shares Hung_By_Son's RMS).
- Checks: SkillCastStatsHostTest 63; CopySpecialBinaryTest 117 (new `verifyAnswerLock` through
  the real loop: 0+8 in one frame, lock 425ms after the answer, 15ms sleep, early frame, 575ms
  fallback, 7's gate, Khóa 575 one send per frame); ModUpLv 271, SpecialAttack 52, Farm special
  1,417, Auto PK 202, buff swap 83, Auto PK cadence 787 (Tắt and Khóa 575 traces identical to the
  source: 229/149 packets), linkage 94, Khoảng chờ UI 56 (button Đáp 425 → 450, label fits),
  isolated 12s emulator startup. Hash-pinned tests ran with catalog copies pointing at `945A5094…`.
- New file only: `Hung_By_Son.jar`, x3 and golden unchanged. Not tested on the live server.
- v2 with the file log (user request while testing): `../jar/Hung_test_attack.jar` replaced,
  SHA-256 `6337BB3AB1F3B06E216353E0CF17EFFB8A847F26972EA7DCAFA65A363DAAA7DC`, stage
  `build/hung-test-attack-20261029-b/`; v1 backed up as
  `../jar/backup/Hung_test_attack-before-filelog-20261029-b.jar`. The patcher's 6-argument form also
  swaps `HungBySonSkillStats.sent`/`footer` from the file-log template variant and adds
  `SkillCastLog`. The file-log footer now keeps the né/không trả lời/trúng/phút line and shows
  "Log lỗi: …" only on a write failure (the no-file-log variant compiles unchanged). Port identical
  to v1. Re-ran: host 63, CopySpecial 122 (file-log catalog flag; real CSV written), ModUpLv 271,
  Special 52, Farm special 1,417, Auto PK 202, buff swap 83, cadence 787, linkage 94 (288 classes),
  Khoảng chờ UI, 12s startup. Test logs went to the stage, not the user's `hso_logs`.

## Hung By Son: Khoảng chờ lock 600/650/700ms, x1/x3 (2026-10-29)

- User request: after Khóa 575ms the mode button continues to 600, 650 and
  700ms. `SkillCastStats.LOCK_MS` = {500, 525, 550, 575, 600, 650, 700}; cycle
  Tắt → 500 → … → 700 → Tắt. Default stays mode 4 = Khóa 575ms; the mode is not
  persisted, so no RMS change. The UI reads labels from `paceLabel`, unchanged.
- x1: `build/hung-lock-700-20261029-a/Hung_By_Son.jar`, 1,406,865 bytes, SHA-256
  `46C6C77F798F3C33A770DA16822C716AA8711029ED9A1FD5DB3594B27AB97F04`. Source
  `CA7B1413…`; current source compiled with `--release 7` reproduced the old
  class byte-for-byte before the edit. Exactly one entry differs:
  `luna/features/SkillCastStats.class` (archive recompressed, so size shrinks).
  `SkillCastStatsHostTest` 51 checks (700ms lock, cycle back to Tắt) and
  `HungBySonSkillStatsUiTest` 52 emulator checks (button 575 → 600ms) pass.
- x3: `HungBySonX3Packer` (new pin `LOCK_700_SHA`) first rebuilt the current x3
  `D50FB685…` byte-identically from `CA7B1413…`, then packed
  `build/hung-lock-700-x3-20261029-a/Hung_By_Son_x3.jar`, 3,305,944 bytes,
  SHA-256 `17A4BEAA9B79BC2FBDA8BBB4A612EF4689B37354F0513A4D00265C0D21AFE04D`.
  Differs only in root/b/c `SkillCastStats.class` and the manifest source hash.
  `HungBySonModUpLvX3HostTest` 45 checks pass, incl. Khóa 700ms per slot.
  The full `build-hung-pk-x3.ps1` suite was not rerun.
- Backups: `../jar/backup/Hung_By_Son-before-hung-lock-700-20261029-a.jar`
  (`CA7B1413…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-lock-700-x3-20261029-a.jar`
  (`D50FB685…`). Other endpoints and golden unchanged. No live-server test.

## Hung By Son: signed character ID gold fix — published x1/x3 (2026-10-29)

- User reported `Vàng hiện tại` stuck at `Đang tính toán...`. The gold bridge
  incorrectly required character ID > 0 in both snapshot and wallet handling.
  Native self initialization reads a signed short, so -32768..-1 and zero must
  remain valid identities. The explicit character snapshot already supplies
  readiness. Removing exactly these two guards restores the initial balance and
  later receipts without changing the five-minute warmup for gold/hour.
- Reference `../jar/ModUpLv.jar` remains unchanged (`E282B0AA…`). Its HUD reads
  the character's gold immediately, subtracts a starting balance and divides
  that net change by session seconds ×3600 for its `/h` display. Hung retains
  the requested positive-receipt total and 5–15 minute rolling rate. Bytecode
  evidence is in `build/hung-gold-audit-20261029-a/`.
- Verified x1 candidate: `build/hung-gold-identity-20261029-a/Hung_By_Son.jar`,
  1,423,904 bytes, SHA-256
  `CA7B14135F358CEA72D0EE6A6761C35BB8465AE0584E3064CA23706B0F2EC3EC`.
  Exactly one class differs from `855A7F66…`: `ExpEstimateBridge`; all other
  classes, resources and manifest are byte-identical. Maintained patcher
  `HungGoldIdentityPatcher` / build and publish switch `-GoldIdentityFix`.
  The initial bridge generator also accepts signed IDs for future fresh builds.
- Regression executes both freshly generated and actual candidate gold methods:
  96 checks, including -32768/-1/0/32767, wallet-before/after-snapshot, receipts,
  spending and disconnect. The actual old packaged methods fail the same signed
  ID test as a negative control. The full previous arithmetic/native/UI suites,
  287-class linkage and isolated 12-second emulator startup pass.
- Verified x3 candidate:
  `build/hung-gold-identity-x3-20261029-a/Hung_By_Son_x3.jar`, 3,305,890 bytes,
  SHA-256 `D50FB685268D3DD36AB1A15F133C73C499034544B2F5403F9F4206E2B697DC3C`.
  884 linked classes, economy 183 checks with negative IDs in every slot, full
  instruction parity for all 45 methods of the changed bridge across three
  slots, launcher 1→2→3→1 and Auto Attack/RMS 299 pass. X1 `verified.json` now
  records Published=true; x3 `published.json` records the exact publication.
- After the user closed the game, both verified candidates replaced their
  release endpoints. Published hashes match the candidate hashes above.
  Backups: `../jar/backup/Hung_By_Son-before-hung-gold-identity-20261029-a.jar`
  (`855A7F66…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-gold-identity-x3-20261029-a.jar`
  (`A6A91A49…`). Other endpoints and golden remain unchanged. The earlier locked
  replacement attempt did not change either release. No server account or live
  gameplay was tested.

## Hung By Son: adaptive EXP/gold rates, wallet tab and inventory HUD, x1/x3 (2026-10-29)

- Published x1: `../jar/Hung_By_Son.jar`, 1,423,910 bytes, SHA-256
  `855A7F66891DFBC0F6A64ABC00A148BB2BBE90D57EF9C416D1C3D85EB1F13A58`.
  Source was the exact previous rolling/fine release `AA16DCCA…`.
  Published x3: `../jar/Hung_By_Son_x3.jar`, 3,305,925 bytes, SHA-256
  `A6A91A495BEB3293796E373CEB900A800520E3A448163A90521BCD468BAE67B9`.
- EXP/hour and gold/hour wait five minutes from the first positive reward.
  From minutes 5–15 they divide received rewards by elapsed real time; afterward
  they use the last 900 one-second buckets (15 minutes). Old rewards expire on
  reads even during idle; warmed windows resume without another warmup.
  EXP level changes do not reset the rate. Fine HUD numerator behavior remains.
- Fourth tab `Vàng / phút` shows current wallet, estimated gold/hour and total
  received since login. The first observed balance is only a baseline; positive
  wallet deltas count receipts, spending changes the balance without subtracting
  receipts. Any observed wallet increase counts, including sales/rewards; a
  combined server update only exposes its net delta. Disconnect/identity change
  resets the session. EXP and gold tabs refresh once per second while open.
- HUD draws `Hành trang: used/capacity` in the native bold yellow font at logical
  x6/y110 below SET1/2/3, using the actual native bag vector size and capacity.
  MOD row cycles BẬT → GIẢN LƯỢC → TẮT → BẬT. ON adds inventory to old details;
  COMPACT keeps only inventory; OFF hides both. Initial default ON inherits the
  old native setting on upgrade; the three-state choice persists in its own RMS.
  The row draws before the old detail gate, verified through the native renderer
  for COMPACT and OFF. No capacity is fabricated before world/bag readiness.
- Exactly seven x1 class entries differ, including two new classes:
  `GoldRateSupport` and `HungInventoryInfo`. Other classes, resources and x1
  manifest are byte-identical. Native wallet hook follows the unique gold-field
  write; snapshot/disconnect use the existing EXP lifecycle. New helper code is
  CLDC-screened, and 23 unrelated skill-statistics methods remain identical.
- x1 checks: adaptive EXP arithmetic 5,424, gold 43, gold native bridge/archive
  37, EXP info 15, estimator 38,735, fine integration 2,359, percent 4,548;
  inventory menu/cycle/RMS/native font previews and four-tab pointer/UI/live
  refresh 52 pass. Native Auto Attack 1,805, cache 262, selection 60, combat,
  equipment, route/controller and other regressions pass. All 287 classes link;
  isolated 12-second startup has empty stderr. Preview images and logs are in
  `build/hung-economy-info-20261029-b/`.
- x3: 884 linked classes, host 1,958, economy all-slots 183, Battlefield 134,
  PK/selection 69 and Auto Attack 299 pass. All 597 methods checked across the
  seven changed classes match x1 after namespace normalization. Gold, EXP rate
  and inventory settings are slot-local; RMS choice survives a fresh loader with
  separate a/b/c records. Shared EXP level backend and suite identity remain.
  Native launcher and held-* transition 1→2→3→1 preserve live client instances.
- Backups: `../jar/backup/Hung_By_Son-before-hung-economy-info-20261029-b.jar`
  (`AA16DCCA…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-economy-info-x3-20261029-final.jar`
  (`B43778D2…`). Publication verifies other endpoints and golden unchanged.
  Proofs: `build/hung-economy-info-20261029-b/verified.json` (published flag) and
  `build/hung-economy-info-x3-20261029-final/{verified,published}.json`.
  Maintained x1 pipeline: `build-hung-auto-pk.ps1` / `publish-hung-auto-pk.ps1`
  with `-EconomyInfo` and
  `HungEconomyInfoPatcher`; x3 uses the pinned `build-hung-pk-x3.ps1` profile.
  No live-server account or gameplay was tested. Feature details:
  [EXP, gold and inventory information](HUNG_BY_SON_EXP_INFO.md).

## Hung By Son: rolling five-minute EXP/hour and fine HUD numerator, x1/x3 (2026-10-28)

- X1 `../jar/Hung_By_Son.jar`: 1,418,744 bytes, SHA-256
  `AA16DCCA0DA0A48DB0C0F39088D402D40390FCC15B779C50142BCF731B10A03C`.
  Source was exact selection-objects release `9C45CB4C…`.
  X3 `../jar/Hung_By_Son_x3.jar`: 3,289,719 bytes, SHA-256
  `B43778D21755CBDB75568D67DDE86D18717F81165D54B4B3E1624B6005B6C309`.
- EXP/hour now uses positive EXP received in the last 300 one-second buckets
  multiplied by 12. Initial warmup stays 300000ms from the first positive
  packet. Old rewards expire even without new packets when the value is read;
  a warmed empty window returns 0, and new gains after idle contribute without
  another warmup. Current second and 299 preceding seconds use a fixed 300-long
  ring (2,400 data bytes per slot); expiry can occur less than one second early
  relative to an exact millisecond window. Real clock, character/session reset,
  level-crossing accounting, positive-only gains and ETA formatting remain.
- HUD numerator now multiplies the estimated level total by the same fine
  percentage shown on the level line. Native progress is fallback before a
  valid fine phase. Fine value participates in the line cache key, so changes
  within the same native 0.1% bucket can update K/M immediately. Compact K/M
  still rounds to one decimal (0.1M = 100,000 EXP). This does not change total
  estimation/readiness/reset rules or claim absolute server EXP accuracy.
- Only `luna/features/ExpRateSupport` and `ExpEstimateSupport` change. Patcher
  verifies 17 rate and 49 estimator methods unchanged; old HUD initialization
  is recovered exactly by removing the added cache initializer. New code passes
  CLDC screening; all other classes, manifest and resources are byte-identical.
- Candidate tests: rolling rate 5,414 checks (list-based randomized oracle,
  boundaries, idle, 20k same-second packets, real-clock resets, BigInteger),
  EXP info 15, estimator 38,735, fine integration 2,359, percent arithmetic 4,548.
  The fine fixture covers 400M/355.7M totals, 32.012%/32.099%, compact rounding
  thresholds without native bucket changes, cache reuse and no extra HUD RMS.
  Cache 262, selection 60, PK 202 and native combat/UI regressions pass;
  285-class linkage and isolated 12-second startup with empty stderr pass.
  The emulator fixture seeds a retained recent reward for the rolling-rate UI.
- X3: 878 linked classes; host 1950, Battlefield 134, PK/selection 69, Auto
  Attack 299, and held-* launcher 1→2→3→1 pass. Rolling/fine fixture passes 40
  checks on the actual three namespaces, including independent arrays, expiry,
  idle/resume, clock resets and numerators changing inside one native bucket.
  Full instruction parity covers 225 methods of both changed classes in all
  slots. Suite identity, RMS isolation and shared level-total backend remain.
- Backups: `../jar/backup/Hung_By_Son-before-hung-exp-rolling-fine-20261028-final.jar`
  (`9C45CB4C…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-exp-rolling-fine-x3-20261028-a.jar`
  (`3866CA49…`). Other endpoints and immutable golden retain their hashes.
  Proofs/logs: `build/hung-exp-rolling-fine-20261028-final/verified.json` and
  `build/hung-exp-rolling-fine-x3-20261028-a/{verified,published}.json`.
  Maintained patcher: `tools/luna/HungExpRollingPatcher.java`; x1 build/publish
  use `-RollingExp`, followed by the pinned `build-hung-pk-x3.ps1` profile.
  No live-server account or gameplay was tested. Tab EXP still refreshes its
  text when opened or switched back to, not continuously while left open.

## Hung By Son: exclude objects from Chọn quái, x1/x3 (2026-10-28)

- Follow-up release also removes `Lửa thiêng` and `con Ma` from the actual
  **Chọn quái** popup, across all levels and live/remembered/saved sources.
  Exact names are trimmed and case-insensitive. Old selected object groups are
  skipped on decode; valid selections remain and object-only profiles behave
  as Tất cả quái. `Xà nữ` remains selectable. Cache/focus fixes are preserved.
- X1: 1,418,343 bytes, SHA-256
  `9C45CB4C061E1A358FFA2A4C5A8BD90E055A075B70BE791F2C97190600CAEDE2`.
  Only `MonsterSelectionSupport.addRow`, `addGroup` and the new private
  `excludedObjectName` helper change from `5E16E5EA…`; all fields, 32 other
  methods, other classes, manifest and resources remain unchanged. New code
  passes CLDC screening; the legacy status method is preserved byte-for-byte.
- Selection candidate fixture passes 60 checks, including mixed/object-only
  old data, live/cached names, case/trim, levels, ordinary selections and exact
  name boundaries. Cache 262, PK 202, semantic/controller/route and existing
  native combat/UI regressions pass; 285-class linkage and isolated 12-second
  emulator startup pass. No live-server gameplay was tested.
- X3: 3,288,193 bytes, SHA-256
  `3866CA4980D43BF903D0DE39373468021C2CC94869990363EB6CF752E6528DCF`.
  All 878 classes link; host 1950, Battlefield 134, Auto PK/selection all-slots
  69, Auto Attack 299 and launcher 1→2→3→1 pass. Instruction parity verifies
  all 102 selection-support methods across three slots against x1. Existing
  identity, RMS isolation and shared EXP backend are retained.
- Backups: `../jar/backup/Hung_By_Son-before-hung-selection-objects-20261028-b.jar`
  (`5E16E5EA…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-selection-objects-x3-20261028-a.jar`
  (`3C6D2D61…`). Other endpoint JARs and golden remain unchanged.
  Exact proofs/logs: `build/hung-selection-objects-20261028-b/verified.json`
  and `build/hung-selection-objects-x3-20261028-a/{verified,published}.json`.
  Patcher: `tools/luna/HungSelectionObjectsPatcher.java`; x1 build/publish use
  `-SelectionObjects`, then the pinned `build-hung-pk-x3.ps1` profile.

## Hung By Son: cache objects and orange Battlefield focus, x1/x3 (2026-10-28)

- Published x1 `../jar/Hung_By_Son.jar`, 1,418,279 bytes, SHA-256
  `5E16E5EAFC1BE81DE19318894F1F4F94787B405635431962964AF09844766E1F`;
  source was the exact current target-lock release `B597B3B7…`.
  Published x3 `../jar/Hung_By_Son_x3.jar`, 3,287,785 bytes, SHA-256
  `3C6D2D61F0A590E1129A0D07C45306D745BC8B69700AFF29E0014854E468FDC6`.
- Only `MonsterCacheSupport.isExcludedTemplate` and
  `HungBySonModUpLv.paintPkRange` change in x1. Every other method, field,
  class, manifest and resource is preserved. The cache excludes trimmed,
  case-insensitive `Xà nữ`, `Lửa thiêng` and `con Ma` names while learning,
  loading, restoring and purging old records. Late catalog discovery removes
  cache-owned placeholders and records for the same server, preserving native
  objects and other servers. Map 48 remains cache-disabled.
- Battlefield player focus uses orange `0xFFA500`, including level 40. The
  native path reads native focus; the active independent Special loop uses
  its locked first target. Xà nữ lines stay red. Existing eligibility,
  radius, FIFO, target lock, skill timing and graphics-state restoration remain.
- Cache fixture passes 262 checks on the candidate; Auto PK binary passes 202,
  including real-painter normal/level-40 focus, Special/native paths, no focus,
  dead/out-of-range players, mode OFF, manual focus and red Xà nữ. Semantic,
  controller, route, native Auto Attack 1805, Farm Special 1417, cadence 787,
  enhancement 68, stamina 45, buff 83, UI and 285-class linkage checks pass.
  X1 isolated startup runs 12 seconds with empty stderr; no account login.
- X3 retains its previous launcher identity, RMS namespaces and shared EXP
  backend. All 878 classes link; host 1950, Battlefield 134, Auto PK all-slots 57
  and Auto Attack 299 pass. Full instruction parity covers 417 methods of the
  two changed classes across default/`b/`/`c/`; launcher held `*` and switching
  1→2→3→1 retain all three clients. No live-server gameplay was tested.
- Backups: `../jar/backup/Hung_By_Son-before-hung-cache-focus-20261028-final.jar`
  (`B597B3B7…`) and
  `../jar/backup/Hung_By_Son_x3-before-hung-cache-focus-x3-20261028-a.jar`
  (`33F45B50…`). Publication checks preserved other endpoint JARs and golden.
  Exact logs/proofs: `build/hung-cache-focus-20261028-final/verified.json`,
  `build/hung-cache-focus-x3-20261028-a/verified.json` and `published.json`.
  Maintained patcher: `tools/luna/HungCacheFocusPatcher.java`; build/publish x1
  use `-CacheFocus`, then the pinned `build-hung-pk-x3.ps1` profile.

## Chibi Final X3 (2026-10-28)

- Published `../jar/chibi_final_x3.jar`, 4,079,293 bytes, SHA-256
  `7CE040DB02C303D843E41784B8C358BA707F0E1A6FAE1DEF4509F78B45235131`.
  Source is the exact current `../jar/chibi_final.jar`
  `A78EFD2FFA52B179A0EF77CABFDB6C5294E91C0BB3BD87635F891EC44A1B1DEB`.
  Launcher template remains `../jar/backup/autotrade_v27_revive05_horse_x3.jar`
  `5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`.
  This is a new release filename; the single source, Chibi Up X3, other releases,
  template and immutable golden retain their hashes.
- One native launcher `MrQuyet.MrQuyet`; suite `HSO CHIBI FINAL X3`, vendor
  `HSO_CHIBI_FINAL_X3`, menu `HSO CHIBI FINAL 1/2/3`. All 217 source game classes
  are copied into default/`b/`/`c/`, plus three Static/support pairs and the
  19 byte-identical wrapper classes: 676 classes and 1,408 ZIP entries total.
  The source's game resources remain byte-identical; a template icon and
  source-manifest virtual INI (`chibi-final-x3.ini`) are added.
- Native RMS operations use wrapper aliases a/b/c and reflective mod configuration
  uses `ahsosky_cfg`/`bhsosky_cfg`/`chsosky_cfg`. Account, settings, list/delete
  boundaries and static clear/restart are isolated. The fresh suite identity
  also separates it from the single client and `chibi_up_x3.jar`; no account
  import is performed. Lifecycle hooks retain native static initialization;
  game class access is made public where required for cross-package Static reset.
  Feature methods and the Hso login, opcode-16/65 readers and opcode-3 capacity/
  ceiling-page fixes are preserved through all three complete transformations.
- `ChibiFinalX3HostTest` passes 17,477 checks: pinned preimages, complete archive,
  wrapper/resource integrity, source parity in every namespace, critical packet
  readers/authentication bindings, 676 JVM class/member links, reflective/native
  RMS save/load/list/delete isolation, unchanged unprefixed records and restart.
  Tests use synthetic memory records; the native account encoder mutates its
  input buffer, so the fixture compares with a pre-save clone.
- All 133 symbols and native legacy route checks pass in all three namespaces;
  semantic/controller/route/Auto Combat/Login Material suites pass. The real
  launcher fixture opens three clients with memory RMS, sends held `*`, and
  switches 1→2→3→1 while preserving instances, canvases and live game loops;
  stderr is empty. Screenshots and logs are under
  `build/chibi-final-x3-20261028/`; exact publication record is `verified.json`.
  No live account login or three-account server gameplay was tested.
- Maintained packer/fixture: `tools/luna/ChibiFinalX3Packer.java`,
  `fixtures/x3/ChibiFinalX3HostTest.java`. Build command:
  `tools/luna/build-chibi-final-x3.ps1 -DependencyDir <external-dependencies>
  -BuildTag <fresh-tag> -Publish`. It pins the fixed source and launcher,
  stages and verifies before publishing, and preserves other releases.
  Usage/data layout: [HSO_CHILD_X3.md](HSO_CHILD_X3.md), Chibi Final X3 section.

## Chibi Final + Luna Final (1): self-character initialization alignment (2026-10-28)

- Người dùng xác nhận hai bản vá trước vẫn kẹt sau chọn nhân vật. Soát sâu hơn tìm thấy
  trường dung lượng túi trong gói nhân vật opcode 3: client cũ đọc một byte rồi lưu vào
  field short, trong khi Hso đọc hai byte. Đây là lần đọc thứ 26 trong phần đầu handler;
  25 vị trí đọc trước đó khớp Hso. Không phải trường dung lượng kho opcode 65 đã sửa trước.
- Payload short-capacity 300 và pet ID -1 được client cũ đọc thành capacity 1 rồi pet ID
  0x2CFF dương. Nó đi nhầm vào nhánh pet, đọc lệch UTF/timestamp và lỗi trước vị trí ghi cờ
  sẵn sàng vào game. Fixture thực thi chính bytecode native từ capacity tới timestamp tái
  hiện lỗi trên đầu vào hợp lệ; đây là tái hiện tổng hợp, không phải packet capture server.
- Published Chibi 1,710,767 bytes, SHA-256 `A78EFD2FFA52B179A0EF77CABFDB6C5294E91C0BB3BD87635F891EC44A1B1DEB`;
  Luna copy 1,684,629 bytes, SHA-256 `7632696AC82F120F03FDB54E882B6619A4A31D39F8BD182CABA5E4495FA16691`.
  Backup lần sửa trước:
  `../jar/backup/chibi_final-before-self-init-entry-self-fix-20261028.jar` (`47C2A7F3…`) và
  `inputs/releases/luna_final-1-before-self-init-entry-self-fix-20261028.jar` (`F46209F1…`).
  Các bản trước lần sửa đầu cũng vẫn được giữ tại đường dẫn ghi ở mục bên dưới.
- Chỉ một phương thức trong receiver đổi so với lần sửa trước: capacity đọc signed short;
  số trang là `max(1,(capacity+41)/42)`, giống Hso. Field capacity giữ short và field pages
  giữ byte. 104 phương thức receiver khác cùng toàn bộ class/resource/manifest/RMS/endpoint,
  chữ ký login và các sửa opcode 16/65 của lần trước được giữ nguyên.
- `EntrySelfProtocolBinaryTest`: 350 checks cho mỗi JAR; 16 payloads gồm capacity
  0/1/41/42/43/127/128/300 với pet vắng mặt và pet tồn tại. Clone đoạn bytecode native
  chạy reader, phép tính pages và nhánh pet thật; các truy cập game-state không liên quan
  được stub. Bản trước lỗi trước timestamp với capacity 300; bản mới và donor đọc đúng
  capacity, pet, tail, timestamp và giữ đúng cursor/trailer. Cờ readiness được kiểm vị trí
  sau timestamp; fixture không tuyên bố đã thực thi toàn bộ thế giới game.
- Full login (7 cases), inventory (166 checks), 133 symbols, 217/194 class-method-field
  linkage, không đổi resource, 23,104 route pairs + 6 path cases mỗi JAR, Chibi legacy-route,
  semantic/controller/route/Auto Combat/Login Material suites và hai lần startup giả lập
  12 giây đều đạt. Hso, `luna_final.jar` không có `(1)`, các endpoint khác và golden giữ hash.
- Chẩn đoán live ở `build/entry-live-diagnostic-20261028/` khởi động MIDlet và callback
  Enter bằng bản sao RMS riêng nhưng không gửi được opcode login 1, không nhận packet.
  Vì vậy không có bằng chứng login/server gameplay; không dùng startup hoặc packet receipt
  để kết luận đã vào world. Dữ liệu RMS gốc không sửa; trace không ghi credentials và không
  dump payload credential opcode 31 hoặc outgoing login.
- Sources: `tools/luna/EntrySelfInitPatcher.java`, `build-entry-self-fix.ps1` và
  `fixtures/binary/EntrySelfProtocolBinaryTest.java`. Exact catalogs/logs/publication record:
  `build/entry-self-fix-20261028/verified.json`. Patcher pins the previous release hashes;
  already-patched and unknown inputs are rejected. The earlier compatibility build below
  records a predecessor which the user reported was insufficient for entry.

## Chibi Final + Luna Final (1): current Hso login and inventory compatibility (2026-10-28, predecessor)

- Người dùng báo chọn nhân vật xong bị chờ mãi, trong khi `Hso.jar` vào được; yêu cầu sửa đúng
  `chibi_final.jar` và `luna_final (1).jar`. Reference hiện tại là `Hso.jar`
  SHA-256 `C69F9C6E9FF3AB998D21C9D78919859E5364C4D28B628723DC74DA48EFDC68C6`.
- Published Chibi 1,710,758 bytes, SHA-256 `47C2A7F3A874133C37301F193B2A4FAA20572A9429DEB43C7D6C3A5C92A06D95`;
  Luna copy 1,684,620 bytes, SHA-256 `F46209F1E1AE9E34729C65DB80B5AEDCD645C9C3B1E4A1CC5A2FD732E76BA06A`.
  Original Chibi `A5C8FC9C…` is backed up at
  `../jar/backup/chibi_final-before-entry-fix-chibi-entry-fix-20261028-verified.jar`;
  original Luna copy `F8998F3E…` at
  `inputs/releases/luna_final-1-before-entry-fix-chibi-entry-fix-20261028-verified.jar`.
  `luna_final.jar` without `(1)`, Hso, all other releases and the golden baseline retain their bytes.
- Exactly two existing classes change per artifact, with 176 other methods in those classes
  preserved. The shared opcode-1 serializer now copies the donor's session-key lookup, bounded
  10×15ms wait, signature calculation and sixth UTF; a remapped donor authentication helper is
  added. Version `9.9.9`, raw credentials, selector/device order and the final `LOCK` UTF follow
  the donor contract. The opcode-13 character selector is unchanged.
- The shared receive parser for opcodes 16/65 now consumes signed shorts for full-list counts
  of subtypes 3/4/5/7 and for the initial storage-capacity field. These five reads replace the
  old byte reads; existing item logic stays native. Resources, manifest, endpoints, identity,
  RMS access and feature/support classes are byte-identical to each input.
- Passed per artifact: seven full native login-payload cases against the current Hso donor,
  helper goldens, Unicode/multiple-session/null-key cases and original-suffix divergence;
  166 receive-protocol checks, including original-reader failures and cursor agreement with
  Hso for counts 0/1/127/128/300 and storage capacity 300; all 133 catalog symbols;
  full `-Xverify:all` class/method/field linkage (217 Chibi / 194 Luna classes), no resource
  changes; 23,104 native next-hop comparisons and six path cases against each input.
  Chibi's existing legacy route regression also passes. The older Luna copy uses its original
  `HSOAutoTradeSupport` route implementation; Chibi uses `LunaGeneratedLegacyHooks`.
- Semantic/controller/route/Auto Combat/Login Material host suites pass. Each artifact runs
  for 12 seconds in isolated headless MicroEmulator with memory RMS and empty stderr.
  No account login, live-server acceptance or gameplay was tested; build/startup checks do
  not establish that the reported spinner has been reproduced or resolved on the server.
- Maintained sources: `tools/luna/ChibiEntryCompatibilityPatcher.java`,
  `ChibiEntryArchiveVerifier.java`, `build-chibi-entry-fix.ps1`, and
  `fixtures/binary/ChibiEntry{Login,Protocol}BinaryTest.java`. Exact catalogs, logs and publication
  record: `build/chibi-entry-fix-20261028-verified/verified.json`. The source patcher pins original
  input/reference hashes and refuses unknown or already-patched input. Rebuilds should use the
  preserved originals; do not feed this updated Chibi to the older MAX pipeline pinned to A5C8.

## Hung By Son x1 + X3: Mode chiến trường khóa mục tiêu HP thấp (2026-10-27)

- Người dùng yêu cầu thêm sau bản ưu tiên HP: trong cùng nhóm, đã chọn mục tiêu máu thấp thì đánh nó tới khi chết,
  ra khỏi vòng, hoặc có nhóm cao hơn xuất hiện mới tính lại (không đổi sang người khác vừa tụt máu thấp hơn).
- Đặc biệt BẬT: field mới `HungBySonModUpLv.pkLock` = mục tiêu đầu của lần `nearestPlayers` trước; lần sau nó được
  offer với HP −1 nên đứng đầu nhóm của nó; mục tiêu đầu mới thành khóa. Nhóm cao hơn tự đứng trên. Ở frame nhóm cao hơn
  xuất hiện, khóa cũ còn đứng đầu nhóm thấp hơn một frame. Tắt chiến trường hoặc danh sách rỗng xóa khóa.
  Đặc biệt TẮT: `priorityRank` tính mục tiêu hiện tại của controller (`AutoAttackRuntime.target()`) như HP 0; controller,
  policy, API, bridge không đổi. Tắt chiến trường: vẫn gần trước, không khóa (như trước).
- x1: patcher `HungAutoPkTargetLockPatcher`, build/publish `-TargetLock`; 66 method port khớp template. Nguồn `E52366BD…`;
  mới `B597B3B7588D5E5692B688A456E2B11B3B7A0A0A4B82EAF0DBAEEFAE0E2B4DBC`, stage `build/hung-auto-pk-target-lock-20261027-b/`
  (tag `-a` dừng ở kỳ vọng sai của fixture tại frame chuyển nhóm), backup
  `../jar/backup/Hung_By_Son-before-hung-auto-pk-target-lock-20261027-b.jar`; 16 endpoint khác và golden giữ nguyên.
  PK binary 157 (21 ca khóa: giữ khi người khác tụt HP, nhóm cao hơn chiếm khóa, chết/ra vòng thì chọn lại, người
  quay lại vòng không giành khóa, Xà nữ chiếm/nhả khóa, tắt chiến trường xóa khóa; đường thường giữ mục tiêu tương tự;
  hai kỳ vọng cũ "đổi sang người gần hơn cùng nhóm" đổi thành giữ khóa). Toàn bộ suite x1 và startup giả lập đạt.
- X3 `33F45B505B2EBA1CB0A2202A7AF493640A06D8CA25A9188A61EA9C267614AD6F` từ x1 `B597B3B7…` (packer thêm `TARGET_LOCK_SHA`),
  stage `build/hung-auto-pk-target-lock-x3-20261027-a/`, publish `-ExpectedX3PreimageSHA256 742355EB…`, backup
  `../jar/backup/Hung_By_Son_x3-before-hung-auto-pk-target-lock-x3-20261027-a.jar`. Fixture X3 trong pipeline đạt;
  `target-lock-slot-parity.txt` port giống x1 ở ba slot, đối chứng X3 cũ trượt ở `nearestPlayers`/`priorityRank`;
  Auto PK binary 157 trên slot 1. Chưa chạy fixture hành vi riêng slot 2/3; chưa thử server thật.

## Hung By Son X3: Mode chiến trường ưu tiên HP thấp trong cùng nhóm (2026-10-27)

- X3 `../jar/Hung_By_Son_x3.jar` `742355EBD346955005085992EBE5654110D8F8DAD0F18E762F313F460FD78E1C` từ x1 `E52366BD…`
  (stage `build/hung-auto-pk-hp-order-20261027-c/`, 285 class, bộ class `53561D6F…` như bản buff swap). Packer thêm
  `HP_ORDER_SHA` dùng chung profile buff swap. Build `build-hung-pk-x3.ps1 -ExpectedSingleClassCount 285 -ExpectedX3ClassCount 878`
  với catalog `auto-attack`/`maze` của stage x1 → `build/hung-auto-pk-hp-order-x3-20261027-a/`; publish
  `publish-hung-farm-spot-x3.ps1 -ExpectedX3PreimageSHA256 347CA62D…`, backup
  `../jar/backup/Hung_By_Son_x3-before-hung-auto-pk-hp-order-x3-20261027-a.jar`; x1, endpoint khác và golden giữ nguyên.
- Fixture X3 trong pipeline đạt (host 1950/878 class, Battlefield 134, Farm spot, ModUpLv 43, Auto PK 45, crowded, Auto Attack 299,
  launcher 1→2→3→1). Ngoài pipeline: `HungX3SlotParity` → `hp-order-slot-parity.txt` (13): `HungBySonModUpLv` và
  `AutoPkTargetPolicy` giống x1 ở cả ba slot (225 method); đối chứng với X3 cũ trượt đúng ở `nearestPlayers`/`priorityRank`
  (`hp-order-slot-parity-negative.txt`). `HungBySonAutoPkBinaryTest` 136 (gồm ca HP) chạy trên slot 1 → `slot1/`.
  Chưa chạy fixture hành vi riêng cho slot 2/3; chưa thử server thật.

## Hung By Son x1: Mode chiến trường ưu tiên HP thấp trong cùng nhóm (2026-10-27)

- Người dùng yêu cầu: Menu mod > Auto đánh > Mode chiến trường, giữ ba nhóm Xà nữ > level 40 > còn lại nhưng trong
  cùng nhóm đổi từ gần → xa sang **HP thấp → HP cao**; HP là HP hiện tại (field entity `bs`, cùng field native dùng
  để coi mục tiêu đã chết), không phải HP tối đa. Lựa chọn mặc định chưa được người dùng xác nhận: HP bằng nhau thì
  gần trước; tắt Mode chiến trường vẫn gần trước, không xét HP.
- Vòng đặc biệt: `AutoPkTargetPolicy.offer(..., hp)` xếp nhóm → HP → khoảng cách, giữ top 20 (hơn 20 người thì giữ
  20 người HP thấp nhất); `nearestSquared` quét cả danh sách. Đặc biệt TẮT: `priorityRank` gói nhóm vào bit 28–29 và
  HP đảo ở 28 bit thấp (chặn 0x0FFFFFFF), controller/API/bridge không đổi. Màu đường kẻ không đổi.
- Patcher `HungAutoPkHpOrderPatcher`, build/publish `-HpOrder`; 66 method port còn lại khớp template, `sendPlayers`
  giữ bản sinh. Nguồn `A2154378…`; mới `E52366BDB974761D106955F3E051346EA513A5D73ABC410D3649ACB5297CDE97`, 285 class,
  stage `build/hung-auto-pk-hp-order-20261027-c/`, backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-hp-order-20261027-c.jar`;
  16 endpoint khác và golden giữ nguyên.
- PK binary 136 (thêm 16 ca HP: nhóm level 40/khác, HP bằng nhau → gần, >20 người giữ HP thấp, Xà nữ HP thấp ở xa
  đứng trước, tắt chiến trường bỏ qua HP, đường thường chọn đúng mục tiêu HP thấp); policy 17.862 so với full-sort
  tham chiếu có HP. Toàn bộ suite x1, cadence 787 và startup giả lập đạt. X3 xem mục dưới. Chưa thử server thật:
  HP người chơi khác client nhận có cập nhật kịp từng đòn hay không.

## Hung By Son X3: Đập đồ, Auto phục hồi thể lực, Auto thay đồ khi BUFF (2026-10-27)

- X3 `../jar/Hung_By_Son_x3.jar` `347CA62D19C421DACD4519C683D79DF81BAD2EAA6A5B2176365059648760DCD2` từ x1 `A2154378…`
  (stage `build/hung-buff-swap-20261027-a/`, 285 class, bộ class `53561D6F…`), build `build-hung-pk-x3.ps1
  -ExpectedSingleClassCount 285 -ExpectedX3ClassCount 878` → `build/hung-buff-swap-x3-20261027-b/` (tag `-a` cùng hash, dừng ở
  `HungBySonAutoAttackX3HostTest` vì danh sách số class nguồn chưa có 285). Packer thêm `BUFF_SWAP_SHA`/`BUFF_SWAP_CLASS_SET`
  (profile PK, ba module mới không mở RMS); publish `publish-hung-farm-spot-x3.ps1 -ExpectedX3PreimageSHA256 47739339…`,
  backup `../jar/backup/Hung_By_Son_x3-before-hung-buff-swap-x3-20261027-b.jar`; x1, các endpoint khác và golden giữ nguyên.
- Fixture X3 sẵn có đạt (host 1950/878 class, Battlefield 134, Farm spot 28, ModUpLv 43, Auto PK 45, crowded 74, Auto Attack 299,
  launcher 1→2→3→1). Kiểm thêm ngoài pipeline: `tools/luna/HungX3SlotParity.java` → `buff-swap-slot-parity.txt` (64): mọi class x1
  mới/đổi từ nguồn X3 trước `C9E5073D…` (10 class, 840 method) giống hệt x1 ở default/`b/`/`c/` sau khi bỏ tiền tố slot và các phép
  đổi của packer (`lib/`, alias mở RMS, `TemMidlet` về gốc, `X3SlotSupport.absoluteResource`), trừ `<clinit>` (lifecycle); X3 cũ
  không có ba module mới. `HungBySonBuffSwapBinaryTest` 83, `HungBySonStaminaBinaryTest` 45 (nhận `cinitclone`/`clears` của X3 là
  nơi ghi thể lực), `HungBySonEnhanceBinaryTest` 68 chạy trên slot 1 của X3 → `slot1/`. Chưa chạy fixture hành vi riêng cho slot 2/3;
  chưa thử server thật.

## Hung By Son x1: Auto thay đồ khi BUFF (2026-10-27)

- Người chơi yêu cầu: chọn chiêu buff đã học trong Menu mod > Cài đặt cấu hình > Auto Tự Đánh > "Auto thay đồ khi BUFF >"; còn 2s hồi chiêu thì Mặc Set 1, buff thành công thì Mặc Set 2; chỉ khi Auto Farm hoặc Tấn công đặc biệt BẬT; chiêu do Farm/đặc biệt tung; mỗi chiêu một chu kỳ; không lưu RMS; chỉ x1. Bytecode: Thay Đồ là module `~lillllil` (Mặc Set 1/2 = −24/−25, cờ đang mặc bật ngay khi set có đồ, mặc chỉ khi công tắc Thay Đồ BẬT); hồi chiêu còn lại `~liil.b` (ms); thành công = gói trả lời opcode 40 có caster là nhân vật, như `HungBySonSkillStats.buff`.
- Thêm (`HungBuffSwapPatcher`, `build-hung-auto-pk.ps1 -BuffSwap`, publish cùng cờ; input ghim `14E2AD66…`): module `HungBySonBuffSwap` (`tools/luna/HungBySonBuffSwap.java.in`); dòng trước "<< Quay Lại" của Tùy chọn tự đánh `~lliiiiil.~lilllili`, `onBuff` ngay sau `HungBySonSkillStats.buff` trong handler opcode 40, đăng ký giữa Stamina và ModUpLv; mỗi chỗ có kiểm nghịch đảo. Mã −115 chọn đầu tiên trùng Mode chiến trường (hằng `ldc`), đổi sang −107. Chi tiết: [HUNG_BY_SON_BUFF_SWAP.md](HUNG_BY_SON_BUFF_SWAP.md).
- x1 `../jar/Hung_By_Son.jar` `A215437818175041B179C5CA841BA36F0EC7ADBFA6A4EB7533CB490931E0EF2F`, stage `build/hung-buff-swap-20261027-a/`, backup `../jar/backup/Hung_By_Son-before-hung-buff-swap-20261027-a.jar`. `HungBySonBuffSwapBinaryTest` 83: menu qua bộ điều phối native, module Thay Đồ thật gửi gói trang bị opcode 11 cho món S1/S2, vòng Tấn công đặc biệt thật gửi yêu cầu buff `40:[14, 0, 1, …]`, trả lời opcode 40 qua handler native (của người khác không tính), Farm hoặc đặc biệt mới chạy, Thay Đồ TẮT/chết/tắt tính năng, bỏ cuộc 5s/2s + chờ 10s, hai chiêu chồng thời gian, set trống tự tắt. Fixture Thể lực và Đập đồ nới kiểm thứ tự module. Toàn bộ suite x1 và startup giả lập đạt. X3 chưa cập nhật. Chưa thử server thật: cấp chiêu cộng thêm của Set 1 có áp vào buff, buff còn giữ sau Set 2, thời gian mặc set so với 2s, opcode 40 cho buff qua đường cast native của Farm.

## Hung By Son x1: Auto phục hồi thể lực (2026-10-27)

- Người chơi xác nhận chạm ô HP/MP trên HUD làm server gửi opcode 59. Bytecode: thể lực (số cạnh trái tim tab Trang Bị) là static `~lliiil.e`, chỉ `<clinit>` và handler opcode 59 `~lllllll.~llilii` ghi; client xin bằng gói 59 rỗng (`~lilillll.~llll(byte)`) khi chạm HUD (kèm mở bảng nhân vật), cuối handler opcode 3 và sau opcode 12 trong `~lllilli.a()`.
- Thêm (`HungStaminaPatcher`, `build-hung-auto-pk.ps1 -Stamina`, publish cùng cờ; input ghim `95D1C5CA…`): module `HungBySonStamina` (`tools/luna/HungBySonStamina.java.in`); dòng menu cấu hình sau Auto Lửa Thiêng, hook đầu bộ lọc hộp thoại `~lillllll.~lilll(String)Z`, báo phản hồi cuối handler opcode 59, đăng ký trước ModUpLv; mỗi chỗ có kiểm nghịch đảo. Ngưỡng thành công dùng ≥ 500 (người dùng đề xuất > 29000). Chi tiết: [HUNG_BY_SON_STAMINA.md](HUNG_BY_SON_STAMINA.md).
- x1 `../jar/Hung_By_Son.jar` `14E2AD66697A67472C7FA90D1786822B98AF787598143C258EED748A7DD8D038`, stage `build/hung-stamina-20261027-a/`, backup `../jar/backup/Hung_By_Son-before-hung-stamina-20261027-a.jar`. `HungBySonStaminaBinaryTest` 45: handler native nhận gói 59 dựng sẵn, bấm dòng menu thật, gói bắt ở hàng đợi gửi native (poll 1s không mở giao diện, giá trị cũ không kích hoạt ăn, ăn rồi hỏi lại, chặn "Sức khỏe đã được phục hồi"/giới hạn ngày, không chặn thông báo kết nối, hết món/chết/timeout). Fixture Đập đồ và ModUpLv nới kiểm thứ tự module/dòng menu. Toàn bộ suite x1 và startup giả lập đạt. Lần publish đầu bị khóa file vì game đang chạy, chạy lại sau khi tắt game. X3 chưa cập nhật. Chưa thử server thật: tần suất 1s, việc dùng Cơm gà bằng op 32 và thời điểm hộp thoại server chưa xác minh.

## Hung By Son x1: thông báo Đập đồ dùng hộp thoại Ok như ModUpLv (2026-10-27)

- Người chơi báo sau `BC8A9103…`: "Đã thêm …" / "… đã có trong danh sách." hiện thành chữ trên đầu nhân vật (như tự chat); ModUpLv mở hộp thoại có nút Ok. ModUpLv `C133.C110(text, 3)` → `C091.C110` (hộp thoại Ok), loại 0 → `C091.C178` (chữ trên đầu); Hung có cùng hộp thoại ở `~liilllii.a(String)`.
- Sửa (`HungEnhancePopupPatcher`, `build-hung-auto-pk.ps1 -EnhancePopup`, publish cùng cờ; input ghim `BC8A9103…`): biên dịch lại `HungBySonEnhance`; thông báo loại 3 của ModUpLv và các lý do dừng mở hộp thoại Ok, khi món cuối rời danh sách thì nối "Danh sách đồ cần đập đã hết." vào cùng hộp thoại; xác nhận menu giữ chữ trên đầu. Câu chữ lấy lại đúng ModUpLv ("Item … đã đạt cấp độ mục tiêu, bỏ qua đập!", "Sắp hết vàng rồi không đập nữa đâu!"). Chi tiết: [HUNG_BY_SON_ENHANCE.md](HUNG_BY_SON_ENHANCE.md).
- x1 `../jar/Hung_By_Son.jar` `95D1C5CA51588B86FA305642B0416A73042E614EED9219ACC89EEB0D8665F7D1`, stage `build/hung-enhance-popup-20261027-a/`, backup `../jar/backup/Hung_By_Son-before-hung-enhance-popup-20261027-a.jar`. `HungBySonEnhanceBinaryTest` 68: mỗi thông báo kiểm hộp thoại native `~llil` thật (fixture nạp metric font như Auto Attack), xác nhận menu không mở hộp thoại; toàn bộ suite x1 và startup giả lập đạt. X3 chưa cập nhật. Chưa thử server thật.

## Hung By Son x1: Đập đồ của ModUpLv thay Auto Cường Hóa (2026-10-27)

- Người chơi báo ModUpLv đập đồ ở đâu cũng được, còn Auto Cường Hóa của Hung phải về làng. Bytecode: Hung tự chặn trong client (map 1, cách (432, 528) ≤ 45, mở phiên NPC pháp sư, chờ tab cường hóa); ModUpLv `C071` không kiểm vị trí/NPC, mỗi khoảng nghỉ gửi một bước op 67 `(0,id,3)` → `(0,id bảo hiểm,loại)` → `(2,0,0)`.
- Sửa (`HungEnhancePortPatcher`, `build-hung-auto-pk.ps1 -EnhancePort`, publish cùng cờ; input ghim `C9E5073D…`): thêm module `HungBySonEnhance` (`tools/luna/HungBySonEnhance.java.in`); nút túi đồ lệnh 110, dòng MOD -32, hướng dẫn sau đăng nhập, hook phản hồi op 67 và loader được trỏ sang module mới, mỗi chỗ có kiểm nghịch đảo. Module cũ `~lliillii` giữ nguyên byte, không còn lối vào. Khác ModUpLv để an toàn: một món mỗi lần, thiếu bảo hiểm/phản hồi loại 3/dưới 6000 vàng thì tắt, bỏ chọn cỏ khi cấp kế tiếp Không dùng, khoảng nghỉ ≥ 1, nhường Auto Mê cung. Chi tiết: [HUNG_BY_SON_ENHANCE.md](HUNG_BY_SON_ENHANCE.md).
- x1 `../jar/Hung_By_Son.jar` `BC8A910363F65FB1785F7D5A2995ECB9301E3B02615C1E9CD11F9A9E5E94D9D2`, stage `build/hung-enhance-port-20261027-b/` (bản `-a` cùng hash, trước khi fixture thêm kiểm đứng ngoài vùng pháp sư), backup `../jar/backup/Hung_By_Son-before-hung-enhance-port-20261027-b.jar`. `HungBySonEnhanceBinaryTest` 56 kiểm trên class thật; toàn bộ suite x1 (gồm hai regression Auto Cường Hóa cũ, guide nhận cả hai thế hệ) và startup giả lập đạt. X3 chưa cập nhật. Chưa thử server thật: chưa xác minh tên vật phẩm bảo hiểm, server chấp nhận đập từ xa với client này, và server có giữ cỏ đã chọn hay không.

## Hung By Son X3: Làng Phủ Sương (Mrs Oda + bán đồ) (2026-10-27)

- X3 `../jar/Hung_By_Son_x3.jar` `477393392892844402F1FA01A7D4F2C8C4E7B3F28ECF98420705845A4F9C8430` từ x1 `C9E5073D…`
  (stage `build/hung-fog-route-20261027-a/`), build `build-hung-pk-x3.ps1` → `build/hung-fog-route-x3-20261027-a/`,
  packer thêm `FOG_ROUTE_SHA` (cùng class set PK `8A5CF6D0…`), publish `publish-hung-farm-spot-x3.ps1
  -ExpectedX3PreimageSHA256 E1109983…`, backup `../jar/backup/Hung_By_Son_x3-before-hung-fog-route-x3-20261027-a.jar`.
- Fixture X3 sẵn có đạt (host 1938/869 class, Battlefield, Farm spot, ModUpLv 43, Auto PK, crowded, Auto Attack,
  launcher 1→2→3→1); chúng không kiểm hai bản sửa mới. Kiểm thêm ngoài pipeline (`tools/luna/HungFogRouteX3Parity.java` → `fog-route-slot-parity.txt`, 22):
  `HungBySonModUpLv.fogTick/codexFogSaleBusy/onDialog` và `update/codexFogHold` của module tìm đường giống hệt x1 ở
  default/`b/`/`c/` sau khi bỏ tiền tố slot; đối chứng X3 cũ khác (dòng 16, không có hold). Packer báo không có tham
  chiếu chéo slot ngoài EXP chung. Chưa chạy fixture hành vi x1 trên từng slot X3; chưa thử server thật.

## Hung By Son x1: bán đồ xong quay lại Làng Phủ Sương (2026-10-27)

- Người chơi báo sau `206D2781…`: Auto đánh bị về Làng Sói Trắng thì vào lại Phủ Sương được; nhưng sau chuyến
  Auto bán rác nhân vật không tới Mrs Oda. Bán xong, bán rác khôi phục Farm ở pha 1 (về map Farm); Farm đặt đích
  tìm đường = map đã lưu (125) mỗi tick. Router native không có cạnh tới 125/126/127, xếp chúng vào vùng 3
  (map ≥ 63) và chọn bước kế là **map 33** từ map 1 (gọi thật `~llili(1,125)` = 33), nên kéo nhân vật qua đá dịch
  chuyển/cổng trước khi `fogTick` (1,4s) kịp chạy.
- Sửa (`HungFogRoutePatcher`, `build-hung-auto-pk.ps1 -FogRoute`, publish cùng cờ; input ghim `206D2781…`): đầu
  `update()` của module tìm đường gọi `codexFogHold()` — Tự động vào Làng Phủ Sương BẬT, đang định tuyến, map hiện tại
  1 hoặc 135, đích khác map hiện tại và bảng cạnh router của đích rỗng, không có chuyến bán dở — thì bỏ qua lượt đó.
  Đích của Farm giữ nguyên; vào map 125 thì Farm pha 1 tự tắt định tuyến, đổi khu, về tọa độ. Đích có cạnh (33…),
  map khác, Phủ Sương TẮT hoặc đang bán: router chạy như cũ.
- x1 `../jar/Hung_By_Son.jar` `C9E5073D6B0978873D6A712C803027951D7E3CE8EDA289AF3803DFC51246C9F7`,
  stage `build/hung-fog-route-20261027-a/`, backup `../jar/backup/Hung_By_Son-before-hung-fog-route-20261027-a.jar`.
  `HungBySonModUpLvBinaryTest` (271) thêm: bước kế native 33, giữ ở map 1 (đích 125/126/127) và 135, không giữ ở 125,
  đích 33, khi đang bán, định tuyến TẮT hay Phủ Sương TẮT (khi đó update chạy như cũ). Toàn bộ suite x1 và startup
  giả lập đạt. Chưa mô phỏng trọn chuỗi bán → Farm pha 1 → Oda trong một fixture; X3 chưa cập nhật; chưa thử server thật.

## Hung By Son x1: Mrs Oda vào Làng Phủ Sương (2026-10-26)

- Người chơi báo: Tự động vào Làng Phủ Sương đưa nhân vật tới Mrs Oda (map 1) rồi đứng yên; nhánh Serena (map 135)
  chạy được. `fogTick` gửi op -30 `(-81, 0, 16)` lấy từ ModUpLv; menu Mrs Oda trên server này có 16 dòng,
  `Làng phủ sương` là dòng cuối (index 15, đếm từ 0 như Phó chỉ huy dòng 0) nên dòng 16 không tồn tại.
  Kết luận dựa trên mô tả/ảnh menu của người chơi, không có packet capture.
- Sửa (`HungFogOdaPatcher`, `build-hung-auto-pk.ps1 -FogOda`, publish cùng cờ; input ghim `4A83A536…`): gửi `(-81, 0, 15)`,
  vẫn gửi thẳng như Phó chỉ huy/Serena. Thêm `codexFogSaleBusy()`: Auto bán rác BẬT, Farm BẬT và trạng thái bán ≠ 0 thì
  nhánh Oda không đi/không gửi, để chuyến bán (bùa về làng → bán ở 696,144 → khôi phục Farm) xong trước; nếu không,
  dịch chuyển sang 135 giữa chuyến làm bán rác bỏ dở mà không trả cờ Farm. Serena và mọi method khác giữ nguyên.
  Template `HungBySonModUpLv.java.in` cập nhật cùng nội dung.
- Sau khi vào map Farm đã lưu, Farm gốc tự đổi khu, về tọa độ và đánh tiếp; Farm không tự tìm đường khi đang ở 127/135
  và tìm đường native không có cạnh map 1 → 135/125, nên vòng quay lại vẫn cần bật Tự động vào Làng Phủ Sương.
- x1 `../jar/Hung_By_Son.jar` `206D2781D8DC22B20DA78ADEC540B19A15ECBC7DBF685FB80E4FC2D0427D4082`,
  stage `build/hung-fog-oda-20261026-a/`, backup `../jar/backup/Hung_By_Son-before-hung-fog-oda-20261026-a.jar`.
  `HungBySonModUpLvBinaryTest` (259) kiểm dòng 15, sáu trạng thái bán chặn Oda, bán xong thì đi/gửi, Farm OFF hoặc
  bán rác OFF không chặn; toàn bộ suite x1 và startup giả lập đạt. X3 chưa cập nhật. Chưa thử server thật.

## Hung By Son X3: sửa reset map và Auto Thú cưỡi (2026-10-26)

- X3 `../jar/Hung_By_Son_x3.jar` `E11099838A4643BB9DABBEB2C1AC6B0D06A34728E2489D7B3AD84EB62728BBF6` từ x1 `4A83A536…`
  (stage `build/hung-mount-guard-20261026-a/`), build `build-hung-pk-x3.ps1` → `build/hung-mount-guard-x3-20261026-a/`,
  packer thêm `MOUNT_GUARD_SHA` (cùng class set PK `8A5CF6D0…`), publish `publish-hung-farm-spot-x3.ps1
  -ExpectedX3PreimageSHA256 31C3DAFA…`, backup `../jar/backup/Hung_By_Son_x3-before-hung-mount-guard-x3-20261026-a.jar`.
- Fixture X3 sẵn có đạt (host 1938/869 class, Battlefield 134, Farm spot 28, ModUpLv 43, Auto PK 45, crowded 74,
  Auto Attack 299, launcher 1→2→3→1). Chúng không kiểm hai bản sửa mới, nên thêm kiểm ngoài pipeline:
  10 method đã vá (`et` getter/xóa/ghi/snapshot/removeSame, reset/update world, `update`/`codexMountMay`/`codexMountSent`)
  giống hệt x1 ở default/`b/`/`c/` sau khi bỏ tiền tố slot (đối chứng x1 cũ báo khác ở `et.a`);
  `HungBySonMapResetRaceTest` và `HungBySonMountGuardTest` chạy trên slot 1 của X3 đạt (race 0/0, mount 18).
  Chưa chạy hai fixture này riêng cho slot 2/3; chưa thử server thật.

## Hung By Son x1: Auto Thú cưỡi không tiêu ngựa khi đang cưỡi (2026-10-26)

- Người chơi báo: ở một số server, Auto Thú cưỡi cứ dùng item ngựa đến khi hết; server đang dùng thì chặn
  dùng ngựa khi đang cưỡi nên không bị. Module (`…lliiilil`) chỉ chạy khi **Auto Farm** BẬT, nhân vật sống;
  mỗi 900 tick game (~18s ở x1, ~6s ở x3; bật lên thì lần đầu sau 20 tick) gửi dùng vật phẩm (op 32) cho
  món đầu tiên trong túi có tên chứa tên ngựa đã chọn. Không đọc trạng thái cưỡi `ef` (−1 = không cưỡi),
  trong khi menu Thú cưỡi gốc chỉ cho chọn ngựa khi `ef == -1`. Vòng dùng item khác của Farm là bùa về làng
  và bình máu/MP, không liên quan ngựa.
- Sửa (`HungMountGuardPatcher`, `build-hung-auto-pk.ps1 -MountGuard`, publish cùng cờ; input ghim `BF1BB40C…`):
  trước lệnh gửi gọi `codexMountMay()`: chỉ gửi khi `ef == -1`; sau lệnh gửi `codexMountSent()`. Sau một lần gửi
  chờ 15s thời gian thực để server cập nhật `ef`; server xác nhận thì đặt lại. Hai lần gửi không được xác nhận
  thì tắt Auto Thú cưỡi, lưu cấu hình và báo "Auto Thú cưỡi: Đã TẮT (server không xác nhận lên ngựa)",
  nên mỗi lần bật mất tối đa 2 ngựa nếu server không gửi trạng thái cưỡi. Giữ cổng Farm/chết/chọn ngựa gốc.
- x1 `../jar/Hung_By_Son.jar` `4A83A53621FAFE241A34C1A396C52D5FF7F36294BC3DF19D3366985C905730D6`,
  stage `build/hung-mount-guard-20261026-a/`, backup `../jar/backup/Hung_By_Son-before-hung-mount-guard-20261026-a.jar`.
- Fixture `HungBySonMountGuardTest` (18) chạy `update()` thật, bắt op 32 tại queue: input đang cưỡi dùng ngựa
  mỗi chu kỳ (5/5); candidate 0; chưa cưỡi dùng đúng ID ngựa đã chọn một lần; xác nhận rồi không dùng nữa;
  xuống ngựa/chết thì dùng lại; chờ xác nhận, thử lại sau 15s, 2 lần thì tắt; bật lại có ngân sách mới;
  Farm tắt/không có ngựa/chết thì không gửi (cả input và candidate). Hồi quy pipeline Auto PK, linkage 282
  class, startup giả lập đạt. Chưa thử trên server thật (cả server chặn và server không chặn).

## Hung By Son x1: tải map không mất vật thể khi khu đông người (2026-10-26)

- Người chơi báo lại lỗi thiếu NPC, bảng khu, đá dịch chuyển sau khi vào map, nghi do đông người
  (30–40 người/khu) hoặc **Tự Áp Sát PvP**. Nguyên nhân tìm được trong `2EB9258B…`: packet 12 hạ cờ nhận
  object rồi gọi reset world trên luồng receiver; vòng world (luồng game) xóa ngay actor vừa được đánh `cL`.
  `et.a` kiểm size rồi mới đọc (hai lock) nên ném lỗi/trả null, reset văng, packet 12 thoát trước khi bật lại
  cờ, packet 4 bỏ mọi record tới lần tải map sau. Vòng world còn xóa theo index: receiver xóa một actor
  phía trước thì index lệch, xóa nhầm actor đang sống. Tự Áp Sát không ghi vào danh sách entity hay cờ
  tải map; chỉ có thể làm tăng số lần gặp. Chi tiết: [báo cáo tải map](HUNG_BY_SON_MAP_LOAD_INVESTIGATION.md).
- Sửa (`HungMapResetRacePatcher`, `build-hung-auto-pk.ps1 -MapResetRace`, publish cùng cờ; input ghim `2EB9258B…`):
  `et.a`/xóa/ghi theo index thành một lệnh `Vector` nguyên tử, ngoài phạm vi vẫn trả null/no-op như gốc;
  thêm `et.codexSnapshot`/`codexRemoveSame` (khối synchronized biên dịch từ Java, bỏ frame cho class 48).
  Reset duyệt bản chụp lấy dưới lock, bỏ qua ô null, vẫn miễn nhân vật chính/pet và ghi marker map cũ.
  Vòng world bỏ qua ô null thay vì văng cả frame, xóa entity theo đúng actor đã xét. Không đổi packet 12,
  thời gian chờ hay cờ tải map; chỉ `et` và `liiliii` đổi, 779 resource/manifest giữ nguyên.
- x1 `../jar/Hung_By_Son.jar` `BF1BB40C7433733FE8416326018B4821D1526BB6BE1B5CA7B992020CE1170647`,
  stage `build/hung-map-reset-race-20261026-a/`, backup `../jar/backup/Hung_By_Son-before-hung-map-reset-race-20261026-a.jar`.
- Fixture mới `HungBySonMapResetRaceTest` (27): reset native thật chạy song song vòng xóa của world (dùng
  hàm danh sách của chính JAR), 1.500 lượt × 5/15/40/100 actor. Input văng 41–93 lượt và bỏ sót actor ở
  642–1.039 lượt; candidate 0/0 với ≥1.433 lượt chồng lấn mỗi mức. Kiểm thêm ngữ nghĩa `et`, xóa theo
  actor khi index lệch, ô null, miễn trừ nhân vật/pet. Đọc danh sách 40 actor nhanh ~2× (đo qua reflection,
  chỉ tham khảo; không phải FPS thiết bị). Hồi quy đầy đủ của pipeline Auto PK, linkage 282 class,
  startup giả lập đạt; 16 endpoint khác/golden giữ nguyên.
- Chưa làm: X3 chưa đóng lại từ x1 mới. Chưa chạy packet 12 trọn vẹn trong fixture (chỉ reset);
  chưa thử khu đông trên server thật.

## Hung By Son x1/X3: nhặt đồ của Tấn công đặc biệt theo menu Auto nhặt đồ (2026-10-26)

- Báo cáo người chơi: Menu mod > Cài đặt cấu hình > Auto nhặt đồ chỉ tích **Cam** nhưng vẫn nhặt **tím**.
  Nguyên nhân trong `896F3B69…`: `HungBySonModUpLv.pickupFrame` (mỗi 28ms khi Tấn công đặc biệt BẬT)
  nhặt toàn map mọi trang bị phẩm `ct >= 3`, không đọc menu; mã 20–49 (menu quy về 0–4) cũng lọt.
  Vòng nhặt toàn map của menu mod chỉ chạy trong cập nhật Farm, mà Farm thoát sớm khi Auto đánh BẬT,
  Farm tắt, mê cung/phó bản hoặc về thành/bán rác — nên không xóa vòng đặc biệt mà cho nó gọi vòng của mod.
- Sửa: `pickupFrame` gọi vòng nhặt toàn map của mod (public static, kiểm `Auto Nhặt` và hàm lọc màu;
  nhãn menu giải mã từ JAR khớp cờ mà hàm lọc đọc), rồi chỉ trong map 48 nhặt thêm **Rương vàng** kể cả
  khi Auto nhặt TẮT. Patcher `HungLootMenuPatcher` (`build-hung-auto-pk.ps1 -LootMenu`, publish cùng cờ):
  input ghim `896F3B69…`, 66 method port khác khớp template (`sendPlayers` là stub sinh bởi patcher PK, giữ
  bản phát hành), chỉ một class đổi.
- x1 `../jar/Hung_By_Son.jar` `2EB9258B2F68805E7F90B74AEAF88DA3CAF691707500B3DF48280FCE10A33C7E`,
  stage `build/hung-loot-menu-20261026-b/`, backup `../jar/backup/Hung_By_Son-before-hung-loot-menu-20261026-b.jar`.
  Fixture CopySpecial (nhánh catalog `loot.menu`): chỉ Cam → nhặt cam (4 và 24) + loại 4/5/7 toàn map, không
  trắng/xanh/vàng/tím; tích Tím → nhặt tím (3 và 23); Auto nhặt TẮT → không nhặt gì. ModUpLv: map 48 nhặt cam +
  Rương vàng, không tím/vàng; Auto nhặt TẮT vẫn nhặt Rương vàng; ngoài map 48 không miễn. CopySpecial 88,
  ModUpLv 248, PK 120, Auto Attack 1805, linkage 282 class, startup giả lập đạt; 16 endpoint khác/golden giữ nguyên.
- X3 `../jar/Hung_By_Son_x3.jar` `31C3DAFA12CBC82D5F7CBDCB860E043908CA22D5D5059070FAC0419EC1B5957C`
  từ `build/hung-loot-menu-x3-20261026-a/` (`build-hung-pk-x3.ps1` với x1 mới; packer chấp nhận thêm
  `LOOT_MENU_SHA` cùng class set PK), publish `publish-hung-farm-spot-x3.ps1 -ExpectedX3PreimageSHA256 8F248E43…`,
  backup `../jar/backup/Hung_By_Son_x3-before-hung-loot-menu-x3-20261026-a.jar`. javap: `pickupFrame` của
  default/`b/`/`c/` gọi vòng nhặt mod cùng slot, không đọc `ct`. Fixture X3 không có ca nhặt đồ.
- Chưa xử lý: vòng nhặt gốc của game (ngưỡng trong cài đặt gốc, toàn map) vẫn có thể nhặt tím nếu ngưỡng
  gốc cho phép, và đánh dấu "đã xử lý" cả món nó bỏ qua. Chưa thử server thật.

## Hung By Son X3 từ x1 Auto PK/Xà nữ (2026-10-26)

- X3 `../jar/Hung_By_Son_x3.jar` `8F248E43FB2AF4FEC476005899DD4C25AF767B89587FAD02B0F1A99A269E46EA`
  từ `build/hung-pk-x3-20261026-c/` (`build-hung-pk-x3.ps1`, publish bằng
  `publish-hung-farm-spot-x3.ps1 -ExpectedX3PreimageSHA256 F7157198…`), đóng từ x1 `896F3B69…`
  (282 class, class set `8A5CF6D0…`). Backup `../jar/backup/Hung_By_Son_x3-before-hung-pk-x3-20261026-c.jar`.
  Publisher xác nhận x1, endpoint khác và golden không đổi.
- So với X3 trước (x1 `F07535B4…`), mỗi slot nhận mọi thay đổi x1 từ đó: lọc đồ nuôi pet/bán rác,
  bùa bán rác, Auto PK (bỏ dòng Tấn công quái thường, PK-only 365px, vòng/đường kẻ, Xà nữ > level 40),
  sửa tải map. Trạng thái chiến trường/PK/đặc biệt riêng từng slot.
- Packer thêm pin PK (hash/class set/282) và cho phép 5 lần mở RMS mỗi slot: hai lần mới là
  `EquipmentFilterSettings` (`hbs_equipment_filters_v1`), đã kiểm đi qua `lib/RecordStore` với prefix slot.
  Fixture ModUpLv X3 chấp nhận bridge Auto PK không còn dòng -117; Auto Attack X3 chấp nhận nguồn 282 class.
  Fixture mới `HungBySonAutoPkX3HostTest` (45): mỗi slot 365px (gọi thật `distanceSquared`), painter
  đỏ/xanh/trắng, literal "Xà nữ", selector/flag/rank bridge/controller trỏ đúng slot của nó.
- Kiểm tra: host 1938 (869 class linked), Auto PK x3 45, ModUpLv x3 43, Auto Attack 299, Battlefield 134,
  Farm spot 28, crowded 74, launcher giữ `*` 1→2→3→1. Hành vi Auto PK chi tiết kiểm trên x1 (PK binary 120);
  X3 kiểm cấu trúc theo slot. Chưa thử server thật.

## Hung By Son x1: Auto PK ưu tiên Xà nữ trong chiến trường (2026-10-26)

- Yêu cầu người dùng: Xà nữ là boss, chỉ xuất hiện 2 lần mỗi chiến trường; khi bật Mode chiến trường
  và có Xà nữ thì **chỉ đánh Xà nữ**. Người dùng chọn: chỉ khi Xà nữ ở **trong 365px**
  (ngoài vòng vẫn PK người), và lúc đó chỉ kẻ **đường đỏ** tới Xà nữ.
- Xà nữ là quái (`cv=1`, tên catalog `cC`), so khớp như BattlefieldSupport/MonsterCacheSupport:
  `"Xà nữ"` bỏ qua hoa/thường, trim; dạng không dấu không khớp. Hợp lệ theo gate quái native,
  không áp nhóm quái của "Tùy chọn tự đánh". Một gói không trộn quái/người, nên khi có Xà nữ
  `nearestPlayers` trả danh sách tối đa 20 Xà nữ và vòng đặc biệt gửi opcode 9.
  Đặc biệt TẮT: `AutoPkPriorityAccess.priorityRank` (2/1/0) thay `priorityTarget`; đòn native
  với mục tiêu chính là Xà nữ vẫn dùng mục tiêu phụ native (có thể gồm quái khác gần đó).
  Tắt chiến trường thì Auto PK không bao giờ nhắm Xà nữ.
- Đổi 4 class: port (selector, `validPkMode`, painter, nhánh gửi; `priorityPlayer` → `priorityRank`),
  `AutoAttackController.findTarget`, API rank và bridge generated. 60 method port còn lại khớp template;
  controller khớp source. Patcher `HungAutoPkSerpentPatcher`, build/publish `-PkSerpent`.
- Nguồn `A6981730…`; mới `896F3B6919C168A3D92FD5455F5DDEA3F0C32D0FA88B3E1A477A2DC55FCF79D8`.
  Stage `build/hung-auto-pk-serpent-20261026-a/`.
  Backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-serpent-20261026-a.jar`.
  Atomic replace/đọc lại hash, bảo toàn 16 endpoint khác và golden đạt.
- PK binary 120 gồm 19 check Xà nữ: chỉ Xà nữ sống trong vòng (tên trim/hoa thường), loại chết/ngoài
  vòng/quái thường, gói opcode 9 chỉ Xà nữ, chỉ đường đỏ; hết Xà nữ → level 40 rồi khác, opcode 6,
  đường xanh/trắng; tắt chiến trường bỏ qua Xà nữ; đường thường chọn Xà nữ trước level 40.
  Auto Attack 1805, Farm Special 1417, ModUpLv 247, chiến trường 65, A/B 787, 282 class, startup đạt.
  Chưa thử gameplay online, chưa xác nhận tên server gửi đúng "Xà nữ".

## Hung By Son x1: Auto PK 365px và đường kẻ tới mục tiêu (2026-10-26)

- Theo yêu cầu người dùng: giảm bán kính Auto PK từ 400 xuống **365px** (vòng xem trước, vòng đặc biệt
  và đường đánh thường cùng dùng `AutoPkTargetPolicy.RADIUS`), và kẻ đường từ chân nhân vật (`aZ/ba`)
  tới chân mỗi người có thể đánh trong vòng. Đường đi qua đúng bộ lọc của `nearestPlayers`
  (native PK, FIFO 10 tên chiến trường, căn vị trí server trước bước bán kính); level 40 màu
  `0x00BFFF` khi bật chiến trường, còn lại `0xFFFFFF`; vòng vẽ đè sau đường kẻ.
  Kẻ tới mọi người hợp lệ trong vòng, không cắt ở 20. Lựa chọn mặc định chưa được người dùng xác nhận:
  đường hiện cùng điều kiện với vòng; tắt chiến trường thì level 40 cũng trắng.
- Chỉ đổi `AutoPkTargetPolicy` và `HungBySonModUpLv.paintPkRange` (không thêm field/method/class);
  patcher `HungAutoPkLinesPatcher`, build/publish dùng `-PkLines365`.
- Nguồn `0227180C…`; mới `A698173091FE7424B86652B59641551869F676AAD5B103AE2E512C38F4B6D3EB`.
  Stage `build/hung-auto-pk-lines365-20261026-a/`.
  Backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-lines365-20261026-a.jar`.
  Atomic replace/đọc lại hash, bảo toàn 16 endpoint khác và golden đạt.
- PK binary 88 gồm 364/365/366, 219-292/259-259, gói opcode 6 ở 365 và 17 check đường kẻ trên painter
  thật (màu, đầu/cuối, scale 2, loại quái/chết/không thù địch/cL/ngoài vòng/FIFO, vị trí server, 25 người,
  mode quái không vẽ). Policy 17.864; vòng native 44+6, world camera 15, menu 75, A/B nhịp 787,
  hồi quy đầy đủ, 282 class linkage, startup 12s đạt. Chưa có ảnh giả lập riêng cho đường kẻ;
  chưa chạy gameplay online.

## Hung By Son x1: không bỏ cả gói NPC/vật thể khi đổi map (2026-10-26)

- Người dùng báo lỗi thỉnh thoảng tải map vẫn thấy nền/người chơi nhưng thiếu toàn bộ NPC,
  bảng đổi khu, đá dịch chuyển; hành trang còn dùng được. Tái hiện được native packet 4
  return cả gói khi lookup gặp entity `cL` còn sót trước lượt dọn world. Reset map chỉ đánh dấu,
  nên dữ liệu mới đến sớm và trùng ID có thể làm mất tất cả record phía sau.
- Thêm marker riêng cho entity thuộc map trước, đặt đúng tại vòng reset với các ngoại lệ native.
  Packet 4, nhánh state 127 và danh sách NPC đầy đủ -50 dùng factory native khi gặp ID map cũ;
  same-map `cL` chỉ skip record, không hồi sinh quái. Sửa hai ALOAD null ở đuôi tạo player state 127.
  Giữ native cleanup; -44 tìm đúng placeholder mới, -50 tạo đúng lớp NPC và packet 4 kế tiếp tái dùng nó.
- Chỉ đổi field entity, hàm reset world và hai hàm receiver; strict inverse đạt cho cả ba class.
  Các class combat/controller/serializer và toàn bộ 779 resource/manifest byte-identical.
  Không tăng delay, không thêm polling tải map hoặc NPC đoán; Auto PK 400px giữ nguyên.
- Nguồn `721671B5…`; mới `0227180CBD4839C468F2AAA466E4F3555A67663181DC21F32419D5817CD98E01`.
  Stage `build/hung-map-objects-20261026-b/`; build/publish dùng `-MapObjectsFix`.
  Backup `../jar/backup/Hung_By_Son-before-hung-map-objects-20261026-b.jar`.
  Đọc lại hash sau atomic replace, bảo toàn 16 endpoint khác/golden đạt.
- Fixture native mới **80 checks**: đối chứng cũ tái hiện, stale ở đầu/giữa/cuối gói,
  người chơi/NPC, same-map không respawn, marker chỉ do reset ghi, state 127,
  phản hồi -44 cập nhật đúng entity mới, -50 đến trước packet 4 không hiện trùng/gửi lại -44.
  Auto Attack 1805, Farm Special 1417, ModUpLv 247, PK 54, menu 75, vòng 44+6/camera 15,
  A/B nhịp 787, semantic/route/equipment/scroll/Battlefield/Maze và 282 class linkage đạt.
  Startup giả lập 12s stderr trống đạt. Chưa có trace đúng phiên lỗi người dùng hoặc gameplay online;
  không khẳng định mọi lỗi có cùng triệu chứng đều đã loại bỏ. Chi tiết và hướng phụ chưa xác nhận:
  [báo cáo tải map](HUNG_BY_SON_MAP_LOAD_INVESTIGATION.md).

## Hung By Son x1: giảm bán kính Auto PK còn 400px (2026-10-26)

- Theo yêu cầu người dùng, giảm đồng thời vòng xem trước và phạm vi chọn PK từ 500 xuống 400px,
  gồm đường Auto PK thường và vòng đặc biệt. Nguồn chung `AutoPkTargetPolicy.RADIUS=400`;
  biên hình tròn gồm đúng 400px, ngoài 400px bị loại. Đường kính vẽ là 800px.
- Recompile cả policy và riêng painter vì Java inline hằng số vào bytecode phần vẽ.
  Chỉ policy và `paintPkRange` thay đổi; bộ chọn quái toàn map, engine chiêu/nhịp, native camera
  hook, menu/RMS và tài nguyên giữ nguyên. Không thêm class (vẫn 282).
- Nguồn `C2F90219…`; mới `721671B5C0762A85CFEF8F09F02E94CEF2111137FD30F74EBD445B4E54AEB7AB`.
  Stage `build/hung-auto-pk-radius400-20261026-a/`; build/publish dùng `-Radius400`.
  Backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-radius400-20261026-a.jar`.
  Atomic replacement/đọc lại hash, bảo toàn 16 endpoint khác và golden đạt.
- Đạt 18.408 policy checks; PK binary 54 gồm 399/400/401, đường chéo 240/320, loại 283/283,
  level 40 ở 400 được giữ/401 bị loại và gói opcode 6 thực sự chỉ chứa người ở 400, không chứa
  người ở 401. Ảnh native vòng 44+6, world camera 15, menu 75; A/B nhịp quái 787 và toàn bộ
  hồi quy/282 class linkage/startup giả lập 12s stderr trống đạt. Chưa chạy gameplay online mới.

## Hung By Son x1: sửa nhãn PK đang chọn và xem trước vòng khi tắt đánh (2026-10-26)

- Ảnh người dùng cho thấy `Auto quái + PK [x]`: patch trước chỉ đổi literal của dòng chưa chọn,
  bỏ sót literal có hậu tố `[x]` do generator tạo riêng. Bộ lọc PK vẫn chỉ chọn người; lỗi nằm ở nhãn.
  Nay cả hai trạng thái đều là `Auto PK` / `Auto PK [x]`.
- Người dùng chọn rõ vòng hiện **ngay khi Auto PK được chọn**, kể cả master Auto và đặc biệt cùng OFF.
  Bỏ điều kiện hai công tắc này khỏi painter; giữ world ready, Farm nhường quyền, tâm/camera/scale
  và bán kính 500px. Chuyển Auto chỉ quái ẩn vòng. Vẽ xem trước không kích hoạt đánh.
- Nguồn `D2E1A331…`; mới `C2F902196E743316DDD842A87E7A5BA31849197FFE9746D26CE0423C42D3A751`.
  Stage `build/hung-auto-pk-preview-20261026-b/`, build/publish với `-PreviewFix`;
  backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-preview-20261026-b.jar`.
  Atomic replacement và đọc lại hash đạt; 16 JAR khác và golden giữ nguyên.
- Chỉ hai method thay đổi: generated adapter `showMenu` và port `paintPkRange`. Mọi method khác,
  bộ chọn mục tiêu, chiêu/nhịp/MP/cooldown/serializer, world camera hook và resource/manifest giữ nguyên.
- Kiểm menu native mới đạt 75 ca: đủ bốn tổ hợp master ON/OFF × quái/PK, chính xác nhãn checked,
  callback -113, không có dòng -117, và loại quái gần hơn người có thể PK. Đối chứng D2 cũ thất bại
  đúng trường hợp `enabled=false, players=true` với nhãn trong ảnh người dùng.
- Ảnh native mới đạt 43 + launcher 6 ca, gồm PK được chọn với cả hai công tắc OFF có vòng,
  mode quái không có vòng. Toàn bộ hồi quy đạt lại: PK 54, world camera 15, A/B cadence 787,
  policy 19.402, native Auto/Farm/phó bản/mê cung/chiến trường/EXP/đồ/bùa/đi buôn/cường hóa;
  linkage 282 class và startup 12s stderr trống. Chưa chạy gameplay online mới.

## Hung By Son x1: sửa vòng Auto PK lệch tâm (2026-10-26)

- Người dùng gửi ảnh ZangVPS-Micro: nét vòng lệch xa nhân vật. Bytecode xác nhận hai callsite
  bị coi nhầm là reset camera thực ra là dịch minimap, nằm sau bước graphics reset của world.
  Painter nhận tọa độ world trong không gian screen nên lệch tâm. Fixture UI trước chỉ gọi
  painter trực tiếp với camera giả lập, không kiểm vị trí hook trong renderer nên đã bỏ sót.
- Chuyển về một callsite sau lớp vẽ focus/entity, ngay trước reset world→screen native.
  Giữ nguyên transform camera, dịch rung bu/bv và scale khi vẽ; không phụ thuộc các nhánh minimap/HUD.
  Chỉ một class world-render thay đổi, mọi class khác và toàn bộ 779 resource/manifest byte-identical.
- Nguồn `3AE3D812…`; mới `D2E1A3318F082F4BF7EEAB4BE06BDB6F4BAED85156899734238032C5022B43B0`.
  Backup `../jar/backup/Hung_By_Son-before-hung-auto-pk-center-20261025-c.jar`.
  Stage `build/hung-auto-pk-center-20261025-c/`, `verified.json` xác nhận build và publication;
  build/publish dùng `-RangeCenterFix`. Atomic replacement, đọc lại hash và bảo toàn 16 endpoint
  khác cùng golden đạt. Tag giữ ngày bắt đầu công việc 25/10; hoàn tất sau nửa đêm 26/10.
- `HungBySonAutoPkWorldRangeTest` đạt 15 kiểm: đối chứng đúng JAR cũ thất bại yêu cầu vị trí hook,
  candidate có một hook trước reset; các phép dịch camera native và reset native được thực thi
  trong pixel worker để kiểm vùng biên tương ứng tâm tọa độ actor. Đây là kiểm pipeline transform
  cùng bytecode callsite; không phải phiên gameplay đăng nhập đầy đủ.
- Hồi quy đạt như stage Auto PK: PK binary 54, policy 19.402, A/B cadence 787 (Tắt và khóa575/retry
  đủ 60s/cấu hình, trace/MP/cooldown khớp E8), ảnh range 39+6, Auto đánh native 1.805,
  Farm đặc biệt 1.417, CopySpecial 85, ModUpLv 247, Special 52, Battlefield 65, Maze 58,
  equipment/scroll/EXP/trade/enhance/NPC/semantic/controller/route, 282 class linkage và startup 12s
  stderr trống. Không thay bộ chọn, bộ chiêu, serializer, cooldown hay nhịp combat; chưa thử lại server thật.

## Hung By Son x1: Auto PK riêng, vùng 500px và ưu tiên chiến trường (2026-10-25)

- Yêu cầu đã chốt: [kế hoạch và nhật ký](HUNG_BY_SON_AUTO_PK_UPGRADE_PLAN.md).
  Hành vi hiện hành: [Auto PK](HUNG_BY_SON_AUTO_PK.md); [báo cáo nghiệm thu](HUNG_BY_SON_AUTO_PK_TESTS.md).
- Nguồn `E8A95AEB…`; mới `3AE3D8126E4041F96F19DD6336C1BEE5F5824477946E6A706C83A7119A8A1B46`.
  Stage `build/hung-auto-pk-20261025-g/`, `verified.json` xác nhận build và publication.
  Backup nguồn: `../jar/backup/Hung_By_Son-before-hung-auto-pk-20261025-g.jar`.
  Atomic replacement và đọc lại hash đạt; 16 JAR endpoint khác, gồm X3, và golden giữ nguyên.
- Bỏ dòng Tấn công quái thường, đổi Auto quái + PK thành Auto PK. Vòng đặc biệt đọc mode,
  gửi opcode 9 cho tối đa 20 quái gần nhất toàn map hoặc opcode 6 cho tối đa 20 người hợp lệ
  trong hình tròn bán kính 500px. PK lọc native/chiến trường trước xếp hạng, ưu tiên level đúng
  40 khi chiến trường bật. Tọa độ server được align trước bước tính phạm vi; hình học dùng long.
- Giữ vòng 28ms, khóa mặc định 575ms, skill order/Trọng kiếm, gate 600ms, cooldown/MP và retry.
  Theo lựa chọn người dùng, đặc biệt vẫn độc lập master ON/OFF. Farm giữ quyền đánh quái,
  phó bản vẫn đứng chốt quái, mê cung vẫn độc quyền; bỏ lần tắt công tắc quái ẩn khi vào mê cung.
  Buff tiếp tục opcode 40 và target bản thân/đồng đội. Khóa controller bao từ chọn mode tới enqueue.
- Đường thường khi đặc biệt OFF cũng là PK-only trong 500px; profile optional của bridge cho
  phép ưu tiên level 40 nhưng không hủy cast hợp lệ đang bận chỉ vì thứ tự người chơi thay đổi.
  Vòng xanh được chèn trước hai nhánh reset world transform loại trừ nhau; giữ scale, clip và màu.
- Thay đúng bốn class có sẵn (port, controller, generated adapter, world render) và thêm hai class
  policy/API; giữ toàn bộ class khác và 779 resource/manifest byte-identical. Native render có inverse;
  port/controller kiểm method ngoài allow-list không đổi; serializer và stats class giữ nguyên byte.
- Kiểm đạt: policy 19.402 (1.000 thế giới đối chiếu full-sort), PK binary 54, range UI worker 39
  + launcher 6, A/B cadence 787; đối chiếu đủ 60s ở cả Tắt và khóa575/retry, irregular 10/16/28/40/80ms.
  Trace timestamp/skill/target, MP và cooldown khớp bản nguồn hoàn toàn: 229 gói khi Tắt, 149 khi khóa575
  với fixture này. Đây là số gói mô phỏng, không phải số trúng/phút server.
- Hồi quy đạt: Auto đánh native 1.805, Farm đặc biệt 1.417, CopySpecial 85, ModUpLv 247,
  Special 52, Battlefield 65, PK corpse 11, Maze 58, equipment 237 + UI 23, scroll 76,
  sale controller 44, equipment policy 47.118, EXP, trade/enhance/NPC, semantic/controller/route;
  282 class linkage và startup giả lập 12s stderr trống. Chưa thử Auto PK mới trên server thật,
  chưa xác nhận server nhận đủ 20 người hay cùng khóa/tầm sát thương như quái.

## Hung By Son x1: nhận diện bùa đúng nhánh Auto Farm (2026-10-25)

- Người chơi báo có bùa trong túi nhưng bán rác không dùng; yêu cầu đối chiếu nhánh Auto Farm
  không đánh quái thì dùng bùa. Bytecode của helper Farm xác nhận tìm tên chứa `Bùa về làng`
  rồi gửi ID thực trong item; không kiểm ID 142, loại 7 hay một trường số lượng riêng.
  Bản `2F07C918…` đã thêm sai những điều kiện đó. Fixture cũ tự dựng bùa với ID/loại phù hợp
  nên không phát hiện lệch hành vi này.
- Sửa: đọc/giải mã marker từ helper Farm trực tiếp khi build, tìm cùng tên trong hành trang và
  gửi ID thực qua cùng service. Chỉ thay `HungSaleReturnScrollBridge.findScroll`; mọi class native,
  service, state chờ/timeout, bộ lọc, menu, RMS, resource và method khác giữ nguyên byte.
- Nguồn `2F07C918…`; mới `E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA`.
  Backup `../jar/backup/Hung_By_Son-before-hung-sale-scroll-farm-match-20261025-a.jar`.
  Stage `build/hung-sale-scroll-farm-match-20261025-a/`; build `build-hung-equipment-filters.ps1 -FarmScrollMatch`;
  patcher `HungFarmScrollMatchFixPatcher.java`. Generator bùa ban đầu cũng dùng cùng cách nhận diện đã sửa.
- Kiểm đạt: 76 binary bùa, gồm gọi chính helper Farm và update bán rác trên cùng vật phẩm,
  đối chiếu opcode 32/payload short theo nhiều ID/loại/metadata và tên có hậu tố; không nhận món
  sai tên chỉ vì trùng ID 142. Fixture thất bại trên bản cũ ở so khớp cùng món với Farm và đạt
  trên bản sửa. Giữ kiểm một lần/chuyến, timeout/lỗi gửi, không có bùa/đồ bán, chờ map,
  map Farm gốc và tắt/bật. 44 controller, 47.118 policy đồ, 237 binary lọc, 23 UI/RMS,
  semantic/controller/route, các suite Auto đánh/Farm/phó bản/mê cung/đi buôn/cường hóa/PK/chiến trường,
  liên kết 280 class và startup giả lập 12s đạt. Không kết nối server trong fixture; chưa thử server thật.
  16 JAR khác đối chiếu hash giữ nguyên, gồm X3.

## Hung By Son x1: ưu tiên Bùa về làng khi bán rác (2026-10-25)

- Nguồn `6F1E025C…`; mới `2F07C9183C568D929F29BC2FE5B12FBD73FBB36CFEA36C48C86CE22A2E6651AF`.
  Backup `../jar/backup/Hung_By_Son-before-hung-sale-scroll-20261025-b.jar`.
  Stage `build/hung-sale-scroll-20261025-b/`; build `build-hung-equipment-filters.ps1 -ReturnScroll`;
  patcher `HungSaleReturnScrollPatcher.java`, semantic `SaleReturnScrollController`.
- Giữ kích hoạt gốc `count >= capacity - 3` và kiểm đủ bộ lọc trước khi đi. Sau khi native lưu map
  xuất phát/tạm dừng Farm/chọn làng, tìm item 142, nhóm 7, số lượng dương trong túi. Nếu có và đang
  sống ngoài các làng bán hàng thì dùng một lần bằng service native opcode 32 + short ID (`00 8E`),
  dừng router và chờ server đổi map. Tới map 1/33/70 thì tiếp tục state 2 bán tại làng đó.
- Không có bùa thì nhánh cũ giữ nguyên. Lỗi gửi hoặc quá 10s chưa về làng thì khôi phục đúng làng
  đích gốc và bật lại router, không dùng thêm bùa trong chuyến đó. Đã ở làng không dùng bùa.
  Tắt bán rác/đổi state hủy chờ; Farm OFF tạm dừng, ON tiếp tục đến làng/dự phòng. Sau bán vẫn giữ
  luồng trở về map Farm gốc. Không có đồ hợp lệ thì tự tắt trước bước dùng bùa.
- Chỉ thêm hai hook vào update module bán rác và hai class support/bridge. Gỡ hook cho đúng
  bytecode cũ; service mạng, menu, RMS, bộ lọc, toàn bộ resource và các method native khác giữ nguyên.
- Kiểm đạt: 44 ca controller; 58 ca binary gồm full 27/30, bốn ô trống không kích hoạt, sai ID/loại/
  số lượng hoặc bùa trong rương không dùng, không có đồ bán không tiêu bùa, bắt gói native tại queue,
  một lần/chuyến, chờ map/điểm bán, lỗi gửi/timeout, lưu map gốc/Farm snapshot, tắt/bật và ba làng.
  Thêm 47.118 policy, 237 binary lọc đồ, 23 UI/RMS, semantic/controller/route và các suite native
  Auto đánh/Farm/phó bản/mê cung/đi buôn/cường hóa/chiến trường/xác PK đều đạt; 280 class liên kết,
  CLDC/version/resource/inverse checks, startup giả lập 12s đạt. Chưa dùng bùa trên server thật.
  16 JAR khác đối chiếu hash giữ nguyên, gồm X3.

## Hung By Son x1: sửa mã ô giày và dây chuyền (2026-10-25)

- Người chơi báo chỉ chọn thể lực, chất lượng đến tím, không giới hạn khóa; auto đã bán đồ khác
  nhưng giữ lại giày xanh. Tái hiện: bản `92EF9789…` không nhận mã 6 trong nhóm thể lực.
- Nguyên nhân: đã đọc nhầm icon nhỏ trong sprite native. Đối chiếu từng frame 20×20 đã phóng lớn
  với bảng loại → ô mặc đồ xác nhận mã 6 → ô số 8 là giày, mã 5 → ô số 4 là dây chuyền.
  Bảng đúng: sức mạnh 8/9; khéo léo 4/5; thể lực 0/1/2/3/6; tinh thần 10/11.
  Không đọc các dòng chỉ số cộng để phân nhóm. Nhẫn 4 được đối chiếu thêm menu nhẫn trái/phải;
  vũ khí 8–11 được đối chiếu thêm chọn nghề native. Đủ mọi loại 0–6, không thiếu/trùng; cánh 7 ngoài yêu cầu.
- Nguồn `92EF9789…`; mới `6F1E025C754DDE82F9D479DB1E623C9B680B419A64084C2ED24B983FF9D00819`.
  Backup `../jar/backup/Hung_By_Son-before-hung-equipment-slots-20261025-a.jar`.
  Stage `build/hung-equipment-slots-20261025-a/`; build bằng `build-hung-equipment-filters.ps1 -SlotFix`,
  patcher `HungEquipmentSlotFixPatcher.java`. Chỉ thay `EquipmentFilterPolicy.groupFor(II)I`;
  mọi class native, bridge, menu, RMS, resource và method khác giữ nguyên byte.
- Kiểm: 47.118 policy, 237 binary, 23 menu/callback/RMS/ảnh giả lập và toàn bộ hồi quy của pipeline trước.
  Binary quét loại 0–11 qua cả bốn mask; vòng bán state 3 native với túi `[dây chuyền 501, giày 502]`
  chỉ chọn ID 502 khi bật thể lực. Bộ nhận giả ở ranh giới request không gửi mạng. Fixture mới
  thất bại trên bản cũ, đạt trên bản sửa. Liên kết 278 class, version/CLDC/resource/allow-list,
  semantic/controller/route, native Farm/Auto đánh/phó bản/mê cung/đi buôn/cường hóa và startup 12s đạt.
  Chưa thử bán/ăn trên server thật. X3 và 15 JAR endpoint khác giữ nguyên (16 JAR ngoài x1 đối chiếu hash).

## Hung By Son x1: lọc đồ nuôi pet/bán rác và dừng khi không có đồ bán (2026-10-25)

- Nguồn x1 `F07535B4…`; bản phát hành `92EF9789FC89BBA7A037F08A6A2A9A4CA9073068A2F820802D04CE84A501BE2D`.
  Backup `../jar/backup/Hung_By_Son-before-hung-equipment-filters-20261025-c.jar` giữ đúng hash nguồn.
  Stage `build/hung-equipment-filters-20261025-c/`; build/publish bằng
  `tools/luna/{build,publish}-hung-equipment-filters.ps1`. X3 và 15 JAR endpoint khác giữ nguyên (16 JAR ngoài x1 đã đối chiếu hash).
- Nuôi pet thêm `Ăn trang bị thuộc loại >`. Bán rác thêm `Chỉ bán đồ khóa [ ]` và
  `Bán rác thuộc loại >`. Bốn nhóm Sức mạnh/Khéo léo/Thể lực/Tinh thần bật/tắt độc lập,
  lưu riêng cho từng tính năng; bỏ chọn tất cả không ăn/bán trang bị. Quy tắc loại theo đúng
  vũ khí chiến binh+sát thủ, nhẫn+dây chuyền, năm loại giáp, vũ khí xạ thủ+pháp sư.
- Giữ các điều kiện phẩm chất/cường hóa gốc; cờ khóa đọc trực tiếp từ vật phẩm, không dò tên.
  Kiểm bán là giao của cả ba bộ lọc. Trước khi kích hoạt chuyến bán lúc túi gần đầy
  (`count >= capacity - 3`), không có món phù hợp thì tắt/lưu/báo và giữ Farm nguyên trạng.
  Đổi cấu hình trong chuyến bán khiến quét hết túi vẫn gần đầy thì đợi luồng gốc thu dọn,
  trở về và khôi phục Farm rồi mới tắt. Không lặp chuyến về làng khi danh sách rỗng.
- Chỉ đổi 2 class module native qua hook có kiểm symbol/action; thêm 3 class policy/settings/bridge.
  Resource và các method native ngoài allow-list giữ nguyên, không rebuild support legacy.
  RMS mới `hbs_equipment_filters_v1`; action nằm trong byte âm -118…-128 đã kiểm không trùng.
- Kiểm tra đạt: 47.114 ca chính sách; 87 ca binary gồm đường update native túi 27/30,
  giữ Farm/busy/trạng thái nghỉ, ngừng sau cleanup và tải lại RMS; 23 ca giả lập menu/callback
  thật/lưu RMS/vẽ 4 màn hình; semantic/controller/route, Auto đánh 1.805, Farm Special 1.417,
  ModUpLv 250, mê cung 58, copy special 85, special 52, offline/liên kết 278 class, chiến trường,
  xác PK, phiên NPC cường hóa, đi buôn và startup 12s. Chưa ăn/bán trên server thật.
- Chi tiết hành vi: [HUNG_EQUIPMENT_FILTERS.md](HUNG_EQUIPMENT_FILTERS.md).

## Hung By Son X3 từ x1 mê cung/phó bản (2026-10-25)

- X3 `../jar/Hung_By_Son_x3.jar` `F7157198FDA382076D2B9C7AB5DD5A473562483754B2A1E476A3426FB0434FB0`
  từ `build/hung-maze-x3-20261025-b/` (`build-hung-maze-x3.ps1`, publish bằng
  `publish-hung-farm-spot-x3.ps1 -ExpectedX3PreimageSHA256 FC0565EB…`), đóng từ x1 `F07535B4…`
  (275 class, class set `1682E5B1…`) thành 848 class. Backup
  `../jar/backup/Hung_By_Son_x3-before-hung-maze-x3-20261025-b.jar` (`FC0565EB…`).
- So với X3 trước (x1 `EA213F39…`), mỗi slot nhận thêm mọi thay đổi x1 từ đó: bỏ menu Auto Phó
  Bản A, đứng chốt cửa quái map 48, Rương vàng, Auto Mê cung độc quyền, so khớp câu Phó chỉ huy
  bỏ dấu và chốt chặn 3 hộp thoại.
- Packer thêm pin MAZE (hash/class set/275); fixture Auto Attack X3 chấp nhận nguồn 275 class;
  ModUpLv X3 thêm kiểm câu giới hạn dạng NFD chỉ tắt vào phó bản ở slot nhận được.
- Kiểm tra: host 1910 (848 class linked), ModUpLv x3 43, Auto Attack 299, Battlefield 134,
  Farm spot 28, crowded 74, launcher giữ `*` và 1→2→3→1. Chưa thử server thật.

## Hung By Son x1: so khớp câu Phó chỉ huy bỏ dấu + chốt chặn lặp (2026-10-25, bản c)

- Người chơi báo bản `26A36172…` vẫn lặp với cài đặt **vào bằng ngọc + Hoàn thành phó bản**,
  trong khi nhánh ngọc đã có luật cho câu "Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần…". Bytecode
  xác nhận gói thông báo server (readUTF text, text2, byte) đưa đúng `text` vào bộ lọc hộp thoại
  ở cả ba dạng hiển thị, và hằng chuỗi trong class là Unicode dựng sẵn đúng. Kết luận: chuỗi
  server khác từng ký tự với hằng so khớp; giả thuyết mạnh nhất là dấu thanh ghép rời
  (U+0300–U+036F), chưa có bản ghi gói thật để xác nhận.
- Sửa: `onDialog` so khớp trên `plain(text)` (bỏ dấu cả dựng sẵn lẫn ghép rời, chữ thường,
  gộp khoảng trắng/dấu câu) cho mọi luật Ngã Tư; câu chứa "nga tu tu than 10 lan" tắt mọi cách
  vào và Hoàn thành phó bản. Chốt chặn: đang bật vào, ngoài map 48, 3 hộp thoại liên tiếp không
  được auto trả lời thì tắt tự động vào, báo "Phó chỉ huy không cho vào phó bản"; trả lời được
  hoặc vào map 48 đặt lại bộ đếm.
- Nguồn `387334F9…`, bản mới `F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600`.
  Backup bản trước `../jar/backup/Hung_By_Son-before-hung-dungeon-daily-limit-20261025-c.jar`.
- Kiểm tra: ModUpLv 250 (134 ký tự dựng sẵn = dạng NFD sau `plain`; câu giới hạn dạng NFD tắt
  cả ba chế độ và không gửi lại op -30; chốt chặn 3 lần, trả lời phí đặt lại bộ đếm), mê cung 58,
  Auto Attack native 1805, Farm Special 1417, các suite còn lại, startup giả lập đạt. Stage
  `build/hung-dungeon-daily-limit-20261025-c/`. Test gọi trực tiếp bộ lọc, chưa phát lại gói
  thật; chưa thử server thật. X3 đóng lại từ bản này ở mục X3 phía trên.

## Hung By Son x1: hết 10 lượt Ngã Tư (bản b, đã thay bằng bản c) (2026-10-25)

- Lỗi người chơi báo với `76314EF2…`: bật vào bằng lượt free/Horae + Hoàn thành phó bản, hết
  lượt thì Phó chỉ huy trả "Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần 1 ngày. Nếu muốn vào tiếp
  bạn phải có vé vào phó bản"; nhánh free không nhận câu này nên cứ 840ms gọi NPC và mở lại
  hộp thoại, phải tắt game.
- Sửa: câu bắt đầu "Bạn chỉ có thể vào Ngã Tư Tử Thần 10 lần" tắt free, ngọc, cả hai và
  Hoàn thành phó bản ở mọi chế độ, báo "Đã hết 10 lượt phó bản trong 1 ngày". Chỉ đổi
  `HungBySonModUpLv.onDialog`; patcher mê cung thêm `onDialog` vào allow-list.
- Nguồn `387334F9…` (`backup/Hung_By_Son-before-hung-maze-exclusive-20261025-f.jar`), bản mới
  `26A36172EAAB23471F7E9177C6FA35C8D122FA0DFB54957D5823AAC0C2CC17AB`. Backup bản trước
  `../jar/backup/Hung_By_Son-before-hung-dungeon-daily-limit-20261025-b.jar`.
- Kiểm tra: ModUpLv 105 (thêm: free gặp câu giới hạn thì tắt hết, hai nhịp sau không gửi
  lại op -30), mê cung 58, Auto Attack native 1805, Farm Special 1417, các suite còn lại của
  `build-hung-maze-exclusive.ps1`, startup giả lập đạt. Stage
  `build/hung-dungeon-daily-limit-20261025-b/`. Chưa thử server thật. X3 chưa đổi.

## Hung By Son x1: Auto Mê cung độc quyền và sửa Farm OFF hủy đòn giữ cửa (2026-10-25)

- Nguồn `387334F942B5534DD3B6ECDD2C3BBDEB638E4060B650C94CC784BDFEB1B08300`.
  Bản mới `76314EF2F9EC83765A23CEF2774743D0CD42F28398A26FEFF3810BCAA870E6BB`.
  Backup `../jar/backup/Hung_By_Son-before-hung-maze-exclusive-20261025-f.jar`.
- Bật mê cung tắt Auto đánh, Farm, đặc biệt, quái thường, đi buôn, cường hóa,
  Lửa Thiêng, Phủ Sương, Ngã Tư/free/ngọc/cả hai. Chặn lệnh bật lại qua registry,
  AutoAttackRuntime, Farm, cường hóa và setter đi buôn; không tự khôi phục khi OFF.
- Quyền đòn giữ cửa theo actor/map/khu/target: Farm OFF nhường cast còn hợp lệ;
  scanner và nhánh chọn chiêu/đuổi quái native nhường quyền, animation/request vẫn chạy.
  Không reset autoMode mỗi 140ms hoặc thử thêm chiêu trong khi đòn còn đang tung.
  OFF, đổi world/actor, chết, mất kết nối, mất mục tiêu nhả quyền và hủy callback cũ.
  HP/MP native vẫn chạy; không đổi cài đặt hồi sinh/đăng nhập.
- Chỉ đổi 7 class có sẵn và thêm `HungMazeCombatController`; 275 class, 779 resource.
  Native edits có kiểm inverse, các method ngoài allow-list và toàn bộ resource/manifest giữ byte.
- Kiểm tra: mê cung 58 (có opcode 9 sau Farm.update, direct toggle, vòng đời, actor cũ,
  trạng thái chết 4, cleanup ngoài mê cung); ModUpLv 101; Auto Attack native 1805;
  Farm Special 1417; Copy Special 85; Special 52; Battlefield 65; PK corpse 11;
  enhancement 13+17, trade, 10 semantic suites, linkage/offline 94, `-Xverify:all`.
  Startup giả lập 12 giây, stderr rỗng; chưa đăng nhập/tấn công trên server thật.
- Source/build: `tools/luna/HungBySonModUpLv.java.in`, `HungMazeExclusivePatcher.java`,
  `build-hung-maze-exclusive.ps1`, `src/luna/features/HungMazeCombatController.java`.
  Stage `build/hung-maze-exclusive-20261025-f/`; `verified.json` và `published.json`.
  Publisher xác nhận các endpoint khác (gồm X3) và golden không đổi.

## Hung By Son x1: B đứng chốt theo hướng quái, bỏ A, nhặt Rương vàng (2026-10-25)

- Published `build/hung-dungeon-stationary-20261025-b/Hung_By_Son.jar`, SHA-256
  `387334F942B5534DD3B6ECDD2C3BBDEB638E4060B650C94CC784BDFEB1B08300`.
  Preimage `EA213F3915D6000262A1C8CB8D7EF05DCCCCCAEE4A87DA7696735F5B1CDFFF6B` backed up as
  `../jar/backup/Hung_By_Son-before-hung-dungeon-stationary-20261025-b.jar`.
  `published.json`: atomic replacement, other endpoints (including X3) and golden unchanged.
- B's entry modes, entry routes, replies, maze, fire and existing Special attack methods remain
  instruction-equivalent to the preimage. Changed four classes, added two class-48 semantic classes;
  resources/manifest unchanged. A's menu action -78 is removed from the row provider; A's tick/action
  are inert, so a retained module instance cannot compete with B.
- `HungDungeonPositionController` consumes only new raw live-notice ingress (same gate-announcement
  idea as Luna), requires the server phrase “Bầy quái vật sẽ xuất hiện ở cửa”, and coalesces duplicate
  direction messages for 2 seconds. No fallback aim before the first notice. 2+ distinct directions
  in one message select minimap tile (27,26). Target pixels are tile centers: (492,492), (804,492),
  (468,804), (660,636). Existing walking auto is held while Special owns combat; only the fixed
  destination is submitted through MovementArbiter. Frame sends wait until the selected tile is reached.
- Rương vàng: case-insensitive exact trimmed item name, only map 48; bypass only type-3 purple+
  cutoff. Existing native op20 sender, processed flag, item kinds, range and all outside-map rules remain.
- Checks: position host 40; ModUpLv native menu/entry/direction/loot 101; Special loop native 85
  including stationary attack frames; native Auto Attack 1805; Farm Special 1417; Special 52;
  Battlefield 65; PK corpse 11; offline/linkage 94 (all 274 classes); enhancement 13+17; trade;
  semantic/controller/route/Auto Attack/Special/Farm/monster/gap suites. `-Xverify:all` passed.
  Emulator: 12 seconds isolated headless startup, empty stderr. No live account/server test.
- Source/build: `tools/luna/build-hung-dungeon-stationary.ps1`,
  `HungDungeonStationaryPatcher.java`, `HungBySonModUpLv.java.in`,
  `src/luna/features/HungDungeonPositionController.java`. Publish with
  `tools/luna/publish-hung-special-attack.ps1`. Behavior: `HUNG_BY_SON_MODUPLV_PORT.md`.

## Luna final: Auto Ngã 4 chờ 35s sau khi bị đá ra làng (2026-10-24)

- Theo yêu cầu: xong Ngã 4 bị đá ra làng thì 35s sau mới đi tới Phó chỉ huy (trước đây 1s + bản đồ nạp xong).
- `N4NpcHomePatcher patch` thêm: `n4StepLeave` `ldc2_w 1000` → `35000` (đúng 1 chỗ; `N4_LEAVE_WAIT_MS` đã bị
  inline); câu báo trong `n4OnLeaveDungeon` đổi thành `Xong phó bản lượt N, đợi 35 giây rồi về chỗ Phó chỉ huy.`
  Sau 35s `n4MapReady()` luôn đúng (vượt `N4_SETTLE_MAX_MS` 30s). Đồng hồ 15s của Bùa về làng chỉ bắt đầu từ B1,
  nên 35s chờ không bị tính vào.
- Build lại từ `8CAE7D96…`; helper `HSON4NpcHome.class` trùng byte với v2, so với v2 chỉ `HSOAutoTradeSupport.class`
  đổi. Backup v2: `../jar/backup/luna_final-before-n4-leave35-20261024.jar`. Published 1,683,482 bytes, SHA-256
  `F8998F3EC1570304DB02C7EDBD8AE844CCA90BE917B621B0F45A3DAA95C62CD2`; build `build/luna-n4-leave35-20261024/`.
- `N4NpcHomeTest` 21 ca đạt dưới `-Xverify:all` (thêm: 10s sau khi bị đá vẫn chờ, 36s thì đi). Chưa thử
  emulator/server thật.

## Luna final: Auto Ngã 4 v2 — đứng đúng chỗ Phó chỉ huy, Bùa về làng sau 15s (2026-10-24)

- Lỗi người chơi báo với bản `58E1DC8A…`: ra phó bản về tới NPC rồi không đứng yên mà đi tiếp, sau đó lỗi.
  Thông báo lỗi cụ thể chưa có. Bản v1 đứng ô sát NPC và tính lại chỗ đứng mỗi khi toạ độ NPC đổi; `notice`
  lọt exception ra `n4Tick` sẽ dừng auto bằng "Lỗi bất ngờ".
- Build lại từ `8CAE7D96…` (bản trước v1, `../jar/backup/luna_final-before-n4-npc-home-20261024.jar`);
  backup v1: `../jar/backup/luna_final-before-n4-npc-home-v2-20261024.jar`. Published 1,683,457 bytes,
  SHA-256 `0A169033B95BA633F42081FDC22F170F64F68E67A4482C127DE4F0AAE4CD60FF`; build
  `build/luna-n4-npc-home-v2-20261024/`.
- `N4NpcHomePatcher patch` giờ còn chèn `if (HSON4NpcHome.guard()) return;` ở đầu `n4StepHome` và
  `n4StepTalk` (frame `F_SAME`), ngoài 2 cặp `homeX()/homeY()`.
- Chỗ đứng: toạ độ thực thể NPC, chốt một lần mỗi lượt; trong 40px quanh NPC (hoặc tới ô thay thế) thì trả về
  chỗ đang đứng nên không giao đường nào nữa. Ô dưới chân NPC không đi được và Noclip tắt thì đi tới ô sát nhất.
  Chưa thấy NPC thì đứng chờ, không còn về (588,564).
- 15s tính từ lúc ở map 1 trong B1/B2; hook không được gọi quá 3s (vào phó bản, tắt auto) là lượt mới. Hết
  15s: dừng đi, về `N4_HOME`, dùng vật phẩm tên "Bùa về làng" (mọi túi; dự phòng id 142 túi tiêu hao, theo
  ModUpLv), chờ 3s + lưới ô rồi làm lại. Không có bùa thì báo và đếm lại 15s; quá 3 lần thì dừng Auto Ngã 4.
- Kiểm tra: `fixtures/luna/N4NpcHomeTest.java` 19 ca đạt dưới `-Xverify:all`; lệnh `useItem4` chạy được
  ngoài game. Chưa chạy emulator, chưa thử server thật; id 142 và túi chứa bùa chưa xác minh trên Chibi.

## Luna final: Auto Ngã 4 tự tìm Phó chỉ huy (2026-10-24)

- Lý do: trên server Chibi, Phó chỉ huy không đứng ở (588,564) nên bước B1 (`n4StepHome`) đi sai chỗ.
- Source `../jar/luna_final.jar` `8CAE7D96…` (bản repoint Chibi bên dưới); backup đã xác minh hash:
  `../jar/backup/luna_final-before-n4-npc-home-20261024.jar`. Published 1,681,655 bytes, SHA-256
  `58E1DC8AE6D1E627E0255F11796108299155BD31A58456B8366F04CA052D152D`; build tại
  `build/luna-n4-npc-home-20261024/`.
- `HSOAutoTradeSupport.class` trong JAR này là bản legacy tự chứa (gần `auto_n4_v12`, không dùng `luna/*`),
  không phải output của `legacy-hook.template` hiện tại, nên vá bytecode chứ không build lại từ source.
- `tools/luna/N4NpcHomePatcher.java gen` sinh `HSON4NpcHome` từ `tools/luna/N4NpcHome.java.in` +
  `symbols/autotrade-symbols.properties`, compile `--release 7` với JAR + MicroEmulator; `patch` đổi đúng
  2 cặp `sipush 588/564` trong `n4StepHome` thành `HSON4NpcHome.homeX()/homeY()` (cùng 3 byte, offset và
  stack map giữ nguyên) và thêm `HSON4NpcHome.class`. Các entry khác giữ nguyên byte.
- Luật tìm: thực thể `kind 2`, ưu tiên tên chứa `phó chỉ huy`, rồi id `-37`, rồi tên chứa `chỉ huy`. Chỗ đứng
  là ô đi được sát NPC (dưới, dưới-trái, dưới-phải, trái, phải, trên…, tối đa 3 ô); chưa có lưới thì đứng
  đúng toạ độ NPC và tính lại khi lưới nạp; chưa thấy NPC thì tạm dùng (588,564) và báo một lần.
  Thông báo `Thấy Phó chỉ huy ở (x,y), tới đứng (x,y).` cho ra số thật khi chạy.
- Kiểm tra: `fixtures/luna/N4NpcHomeTest.java` 6 ca (chưa có NPC, chọn đúng NPC giữa nhiều NPC, ô dưới,
  ô dưới bị chặn, chỉ có id, NPC biến mất) đạt dưới `-Xverify:all`, nạp được cả class đã vá lẫn class mới.
  Chưa chạy emulator, chưa thử server thật; khoảng cách server cho mở menu NPC chưa xác minh.

## Luna final: endpoint và tên server HSO CHIBI (2026-10-24)

- Source: `../jar/luna_final.jar` do người dùng đặt vào, 1,679,169 bytes, SHA-256
  `DEDCEF3EF8482ABEBCFD1C8B889801AC7C80AF8B4495CE0207E665705E9F21E0` (Luna sạch, 920 entries, không có
  lớp `luna/*`; chưa có trong record trước đó). Backup đã xác minh hash:
  `../jar/backup/luna_final-before-chibi-endpoint-20261024.jar`.
- Published `../jar/luna_final.jar`, 1,679,150 bytes, SHA-256
  `8CAE7D9622C789D571C8763CB8F69EB73D8821DE07263BE2F0F3BC45B3198140`; build tại
  `build/luna-chibi-endpoint-20261024/`.
- Công cụ `tools/luna/LunaEndpointRepointer.java` (không cần ASM) chỉ thay Utf8 literal chính xác:
  host LUNA mã hoá XOR 5 (`103.178.235.83`) → `103.77.173.232`, nhãn `LUNA` → `HSO CHIBI`, ba IP server
  ngoại ẩn → `103.77.173.232`; manifest `MIDlet-1`/`MIDlet-Name` → `HSO CHIBI`, giữ vendor `Truongbk`.
  Cổng `19129` vốn đã trùng `chibi_up.jar`; không đổi login/version.
- Kiểm tra: thứ tự/kiểu nén 920 entries giữ nguyên, `testzip` đạt, chỉ manifest + 3 class đổi; ba class
  đã sửa trùng byte với class tương ứng trong `../jar/chibi_final.jar`; không còn `LUNA`/`HSOLUNA`/IP cũ.
  Đổi MIDlet-Name nên RMS (tài khoản đã lưu) tách khỏi bản HSOLUNA. Chưa chạy emulator, chưa thử server thật.

## Hung By Son x1 + X3: buff skills as opcode-40 requests (2026-10-24)

- Live report (Hung_By_Son lock release): skills 1–8 hit >90%, but the buff "Đạn xuyên giáp" (14,
  +20% damage, 45s cooldown) showed 47,5% and never applied. Cause: ModUpLv's loop (and the port)
  sent every skill as an attack (opcode 9). The native client builds a buff cast with
  `player.n("")(skill, player)` (template type 1 → marks the cast object `di.g = by`, list = the
  player + same-type allies in range up to the row's target count) and sends opcode 40
  `(byte skill, byte target type, byte n, n × short id)` from `lillill`; the server answers with an
  opcode-40 message `(byte, byte caster type, short caster, byte skill, int time, short target, …)`
  handled by `lllllll.n("liilll")` (dispatcher `liiil`, case 40).
- Change: `HungBySonModUpLv.attackFrame` sends type-1 skills through the native builder (restoring
  `di.g`) and the opcode-40 serializer `lilillll.n("")(BBLet;)V`; the patcher hooks that serializer
  (`sent`) and the buff answer handler (`HungBySonSkillStats.buff`: caster = local player → hit), each
  with an exact inverse. Buffs still take the lock like other skills. Applies in every pacing mode.
- x1 `EA213F3915D6000262A1C8CB8D7EF05DCCCCCAEE4A87DA7696735F5B1CDFFF6B`
  (`build/hung-lock-buff-x1-20261024-a/`, input `193BA880…` now read from
  `../jar/backup/Hung_By_Son-before-hung-lock-x1-20261024-a.jar`), backup
  `../jar/backup/Hung_By_Son-before-hung-lock-buff-x1-20261024-a.jar` (`4F6C422C…`).
- X3 `FC0565EBCABF58344FD6B5A4B00F36DBD066116E13CDE7158085F872D2956FD3`
  (`build/hung-lock-buff-x3-20261024-a/`, 839 classes, packer LOCK pin → `EA213F39…`, class set
  unchanged `3E9309AB…`), backup `../jar/backup/Hung_By_Son_x3-before-hung-lock-buff-x3-20261024-a.jar`
  (`816C2C76…`).
- Checks: CopySpecialBinary 80 (buff: opcode-40 packet `40:[14, 0, 1, id]`, 45s cooldown, other
  player's answer ignored, own answer = hit, no retry); X3 ModUpLv 41 (per-slot buff request and
  answer hooks), X3 host 1898, launcher, Auto Attack 299, Battlefield 134, Farm spot 28; full x1
  regression set and emulator startup. Not yet tested on the live server.

## Hung By Son x1 + X3: lock pacing released (2026-10-24)

- x1 `../jar/Hung_By_Son.jar` `4F6C422C6CCD3385E7936F570F1D7BD34D0B9D04EC0463142108B5238287C350` from
  `build/hung-lock-x1-20261024-a/` (`build-hung-copy-special.ps1 -Release`), input `193BA880…`, verified
  backup `../jar/backup/Hung_By_Son-before-hung-lock-x1-20261024-a.jar`.
- X3 `../jar/Hung_By_Son_x3.jar` `816C2C7681E6BD2C6C739BBF6B2F04CFC4E36191B2632E1AB500E057DFE90185` from
  `build/hung-lock-x3-20261024-b/` (`build-hung-lock-x3.ps1`), packed from x1 `4F6C422C…` (272 classes,
  class set `3E9309AB…`) into 839 classes; verified backup
  `../jar/backup/Hung_By_Son_x3-before-hung-lock-x3-20261024-b.jar` (preimage `A070EEBE…`).
- Same features as `Hung_By_Son_copy_special_lock.jar`, but default **Khóa 575ms** (live test of the
  lock build: Tắt 81–85 hits/min 42%; Khóa 500 113/min 67%; 550 130–134/min 84–88%; 575 137/min 92%)
  and **no file log**: the patcher's `--no-file-log` drops the `//@file-log` lines of the stats
  template and does not add `SkillCastLog`; the "Khoảng chờ" footer shows né / không trả lời /
  trúng/phút instead of the log path.
- `HungBySonX3Packer` gains the LOCK pins; `HungBySonModUpLvX3HostTest` (41) also checks per-slot
  canvas frame, serializer/combat/EXP hooks, no file log, and per-slot counters/mode/lock;
  `HungBySonFarmSpotX3HostTest` turns Tấn công quái thường OFF first (default ON now);
  `HungBySonAutoAttackX3HostTest` accepts 272 source classes.
- Checks: x1 — CopySpecialBinary 71, SkillStatsUi 44, SkillCastStatsHost 46, full regression set,
  offline 272, emulator startup. X3 — host 1898 (839 linked), launcher 1 → 2 → 3 → 1, Auto Attack 299,
  Battlefield 134, Farm spot 28, ModUpLv x3 41, crowded cache. No live-server test of these two files.

## Hung By Son copy special lock: lock pacing, priority, retry (2026-10-24)

- New endpoint file `../jar/Hung_By_Son_copy_special_lock.jar` (no file replaced, no backup needed),
  from `build/hung-copy-special-lock-20261024-a/`, SHA-256
  `FBFBEE74ED5265B5907B88129F85B2648B34B09B72990BAAFBD32D46E5B92F81`, input x1 `193BA880…`;
  `Hung_By_Son.jar`, `_copy_special_skill` (`5E2A225D…`), `_copy_special_gap` (`70D3FC06…`) and
  `ModUpLv_skill_stats.jar` (`76231D16…`) unchanged (`published.json`).
- Evidence: the user's live gap log (Chiến binh) — rule in `HUNG_BY_SON_MODUPLV_PORT.md`
  ("Kết quả đo trên server thật"). Design agreed with the user: area skills first, 1–6 by level,
  optional retry of a missed skill.
- Changes: `SkillCastStats` (answer → newest waiting cast ≥30ms old, miss window 700ms, lock pacing
  Tắt/Khóa 500/525/550/575 with default 550 and no lock after skill 0, retry flag once per skill until
  answered), `HungBySonModUpLv.attackFrame` + `lockOrder`/`rank` (port gains eight methods), UI buttons
  in `HungBySonSkillStats` (Chế độ / Tung lại / Xóa số đo). Pacing Tắt keeps ModUpLv's order exactly.
- Checks: SkillCastStatsHostTest 46; HungBySonCopySpecialBinaryTest 75 (legacy order with Tắt; lock
  mode through the real loop: Trọng kiếm first, 8 before 7, 550ms lock, 600ms gate of 7/8 not
  restarted by Trọng kiếm, 1–6 by level, retry after 700ms once, log note); HungBySonSkillStatsUiTest
  44; full regression set, offline 273, emulator startup passed. `build-moduplv-skill-stats.ps1` now
  also compiles `SkillCastLog` (host test needs it); check build `moduplv-skill-stats-check-20261024-c`
  passed (`BD13E9A6…`, not published).
- Unverified: live server (hits/min, whether skill-0 interleave and retries are accepted, MP use).

## Hung By Son copy special gap: gap measurement, pacing and file log (2026-10-24)

- New endpoint file `../jar/Hung_By_Son_copy_special_gap.jar` (no file replaced, no backup needed),
  published from `build/hung-copy-special-gap-20261024-h/`, SHA-256
  `70D3FC06B656CA90449B30D9F69AB93E083C2526C1A0C12FA25FAED4978A6849`, input x1 `193BA880…`;
  `Hung_By_Son.jar` and `Hung_By_Son_copy_special_skill.jar` (`5E2A225D…`) unchanged (`published.json`).
- Reason: the live comparison gave Hung 44,5% / ModUpLv 44,8%; skills 7/8 (600ms gate after any send)
  hit ~100%, Búa địa chấn (sent 28ms after Hỏa kiếm, same cooldown) 16%. Hypothesis (user): after a
  cast the server needs a per-skill gap X before the next one hits. This build measures it.
- Behavior: `HUNG_BY_SON_MODUPLV_PORT.md` (tab Khoảng chờ, chế độ gửi, file log).
- Sources: `src/luna/features/SkillCastStats.java` (send-order log, né/không trả lời, gap bins 25ms by
  previous hit and by own skill, step fit, pacing, CSV lines), new `src/luna/features/SkillCastLog.java`
  (background writer, `java.io`), `tools/luna/HungBySonSkillStats.java.in` (three tabs),
  `HungBySonModUpLv.attackFrame` (pacing gate, target count/nearest distance). The patcher adds
  `SkillCastLog`; eight classes changed, three added (273). The build script now produces this file
  (`build-hung-copy-special.ps1`); test casts log into `build/<tag>/skill-gap-logs/`.
- Checks: SkillCastStatsHostTest 40 (split, send-order folding, fit, pacing, CSV lines, file write);
  HungBySonCopySpecialBinaryTest 61 (+ 500ms pacing through the real loop, log line of a real
  serializer/combat answer); HungBySonSkillStatsUiTest 42 in MicroEmulator (three tabs, gap tab rows,
  header/row/buttons, breakdown; screenshots `gap-tab.png`, `gap-detail.png`); full regression set,
  offline 273 classes, emulator startup passed.
- Test note: `HungBySonExpInfoBinaryTest` failed once (build -e) when the emulator canvas thread widened
  the dialog while the test switched viewport size; reruns -f/-g/-h passed. Test-only race.
- Unverified: live server; whether `user.home` is writable in the user's emulator (the tab shows the
  path or the error).

## ModUpLv skill stats: cast/miss table for comparison (2026-10-24)

- New file `../jar/ModUpLv_skill_stats.jar`, SHA-256
  `76231D16A03FA8BC6D045FFC78D63DC18BBA542DEC86E2E6ACFFDCBA383A3A4E`, from `../jar/ModUpLv.jar`
  `E282B0AAD121205F9A0CFAF14104C9A9ED281B6838AE22D7FEDD4004E83F997C` (unchanged; `published.json`).
  Build `build/moduplv-skill-stats-20261024-a/`, script `tools/luna/build-moduplv-skill-stats.ps1`
  (rebuild gave the same hash).
- Reason: the user's live test of Hung_By_Son_copy_special_skill showed 41.9–46.3% hit rates at
  speed x0.8/x1/x3; the same counters in ModUpLv allow a direct comparison.
- `ModUpLvSkillStatsPatcher` resolves names via `symbols/moduplv-aliases.properties`, generates
  `ModUpLvStatsAccess` (signatures checked against the stub) and adds six hooks with exact
  inverses: attack manager (reset on Special ON), opcode 9/6 serializers, combat answer handler,
  menu row 17/20 in "Tan cong / muc tieu", its handler, game paint. Adds `ModUpLvSkillStats`,
  `ModUpLvStatsAccess`, `luna/features/SkillCastStats` (class 47 like the input); five classes change.
- Checks: SkillCastStatsHostTest 15; ModUpLvSkillStatsUiTest 13 in MicroEmulator (all 208 classes
  link, counting through the real serializer and combat handler, menu handler opens the native Ok
  dialog, table painted, reset on Special ON). No live-server test.

## Hung By Son copy special skill: ModUpLv attack loop + skill cast tab (2026-10-24)

- New endpoint file `../jar/Hung_By_Son_copy_special_skill.jar` (no file replaced, no backup needed),
  published from `build/hung-copy-special-20261024-b/Hung_By_Son_copy_special_skill.jar`, SHA-256
  `5E2A225DB96E2D25BD9EE3489594A35C746C2E536923531F7E19DDE74EAE2187`, input x1
  `193BA880410CE79C1398C2074FE43239A1F238A2C96D78D9530F9E19869162D9` (unchanged, `published.json`).
  X3 not built.
- Behavior and sources: `HUNG_BY_SON_MODUPLV_PORT.md` (vòng đánh, nhặt đồ, tab tung chiêu).
- `tools/luna/HungBySonCopySpecialPatcher.java` recompiles `HungBySonModUpLv` (released methods
  canonical-identical, six loop methods added) and adds `HungBySonSkillStats`; six hooks, each with
  an exact inverse:
  - canvas `run()`: `frame()` before repaint, `frameSleep()` on both `Thread.sleep` arguments;
  - the opcode-9/6 attack serializers: `sent(skill)` first;
  - the combat answer handler: `combat(stream)` first (mark/reset);
  - game paint: `paint(g)` right after the active dialog;
  - `ExpInfoModalGuard.press/release`: tab press/release first;
  - `ExpInfoBridge.open`: the EXP text through `expText`.
- Controller: Tấn công quái thường ON by default; with Special it stops Auto đánh targeting and the
  Auto đánh/Farm Special chooser, Farm spot progress = loop sends. Counters reset on Special ON.
  `SkillCastStats` added. Eight classes changed, two added (272).
- Checks:
  - HungBySonCopySpecialBinaryTest 52: hooks, skill order/MP/cooldown/targets, 600ms gate ends the
    frame, 20 nearest, cooldown/MP bookkeeping, 28ms frames and canvas wake-up, pick-up, counters via
    the real serializers and combat handler, 2s miss window, reset.
  - HungBySonSkillStatsUiTest 23 (emulator): tabs through the native pointer route, table rows and
    totals, live growth, Xác nhận close; screenshots in the build folder.
  - SkillCastStatsHostTest 15, NormalMonstersHostTest 23. Legacy Special/Farm tests run with
    Tấn công quái thường OFF (the path that remains for it); ModUpLv test expects default ON.
  - The full regression set (Auto Attack 1805, Farm special 1417, timing 2605, crowded 45044, ...)
    and emulator startup passed.
  - No live-server test: hit rate, server acceptance of 28ms sends to 20 far targets and map-wide
    pick-up are unverified.
- Build: `tools/luna/build-hung-copy-special.ps1`. `HungBySonModUpLv.java.in` and the controller
  are shared with the x1/X3 port pipelines, which now pick up the loop source and the default-ON
  option; their released bytes (`193BA880…`, `A070EEBE…`) are unchanged but no longer rebuild
  byte-for-byte from the current sources.

## Hung By Son X3: ModUpLv features from current x1 (2026-10-24)

- Published `build/hung-moduplv-x3-20261024-b/Hung_By_Son_x3.jar`, SHA-256
  `A070EEBE8C90E4B0AD39F4D74E684D429CA29A2F730B403CF4209CB96C4AE7D5`, packed from x1
  `193BA880410CE79C1398C2074FE43239A1F238A2C96D78D9530F9E19869162D9`, 270 classes, class set
  `95F659D49ED2349D76B79998447CB6289DD130D7823EA9C058E0D80443457DD1`, into 833 classes.
  Verified backup: `../jar/backup/Hung_By_Son_x3-before-hung-moduplv-x3-20261024-b.jar`
  (preimage `7FB67D93…`).
- Includes every x1 change since the previous X3:
  - Farm spot stuck policy and UI-open Farm sends;
  - Special 30ms;
  - Battlefield static objects;
  - the ModUpLv port.
- `HungBySonX3Packer` gains the MODULV pins.
- Test changes:
  - `HungBySonFarmSpotX3HostTest` accepts the eighth bridge method (`farmSpotCancelCast`);
  - `HungBySonAutoAttackX3HostTest` accepts 270 source classes.
- `HungBySonModUpLvX3HostTest` (23 checks):
  - every slot has its own class-48 `HungBySonModUpLv`, referencing only its own slot;
  - each slot's five hooks call that slot's copy;
  - the normal-monsters option and the Mê cung state are independent per slot.
- Also passed: X3 host (1890 checks, 833 linked), Battlefield (134), Farm spot (28),
  crowded cache (74), launcher 1 → 2 → 3 → 1, Auto Attack RMS/virtual input (299). No live-server test.
- Build: `tools/luna/build-hung-moduplv-x3.ps1`; publisher: `tools/luna/publish-hung-farm-spot-x3.ps1`.

## Hung By Son x1: ModUpLv features — normal monsters, Lửa Thiêng, Phó bản / Mê cung (2026-10-24)

- Published `build/hung-moduplv-port-20261024-c/Hung_By_Son.jar`, SHA-256
  `193BA880410CE79C1398C2074FE43239A1F238A2C96D78D9530F9E19869162D9`, from
  `4842A7CA88C337FB1E9E37DFE45CB85C684458E50B0B1E0148791D0F2ECA1F12`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-moduplv-port-20261024-c.jar`.
  `published.json` confirms other endpoints and golden unchanged. X3 not rebuilt.
- Behavior and sources: `HUNG_BY_SON_MODUPLV_PORT.md`.
- `HungBySonModUpLv` is compiled from `tools/luna/HungBySonModUpLv.java.in` against the input
  and stored as class 48. Five hooks, each with an exact inverse:
  - the Special send's target builder call;
  - Auto đánh menu row -117;
  - Cài Đặt Cấu Hình row before "Reset";
  - the mod dialog filter;
  - the module loader (registers the module last).
- Controller/runtime gain the normal-monsters option and `engageMonsters()`. Seven classes
  changed, one added (270).
- Checks:
  - HungBySonModUpLvBinaryTest: 62 checks — real menus, the native builder 3 vs map-wide 20,
    and captured packets for every module path.
  - NormalMonstersHostTest: 12 checks.
  - Row-count expectations in the Auto Attack, input, Battlefield and Special tests now include
    the new row; the Special structural check accepts the wrapper.
  - The full regression set and emulator startup passed.
  - No live-server test.
- Build: `tools/luna/build-hung-moduplv-port.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`.

## Hung By Son x1: local movement paced like ModUpLv (2026-10-24, rolled back)

- Rolled back the same day at the user's request ("không đạt" in live play); the
  release is again `4842A7CA…`, restored byte-for-byte from the verified backup.
  The failure details were not reported; the build stays in `build/` for reference.

- Published `build/hung-move-pace-20261024-a/Hung_By_Son.jar`, SHA-256
  `BF37EE0A4D68DFFCA85C3C3731237FE8A68F2DF5F4AA5408E4746400C566FBCD`, from
  `4842A7CA88C337FB1E9E37DFE45CB85C684458E50B0B1E0148791D0F2ECA1F12`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-move-pace-20261024-a.jar`.
  The first attempt stopped before replacement while the emulator held the JAR open;
  the retry succeeded. `published.json` confirms other endpoints and golden unchanged.
  X3 not rebuilt.
- Change: in the native entity update the local player's velocity step goes through
  `luna.features.MovePace` (one step per 28ms of real time, carry-over, restart after a
  ≥112ms backlog) instead of the tempo's 5-of-8 tick mask. Every menu speed now walks at
  ~35.7 steps/s like ModUpLv. Before: x0.8 25, x0.9 28.1, x1 31.3, x2 62.5, x3 93.8.
  Step size (6 + server mount bonus) stays native; other entities keep the mask.
  Research: `MODUPLV_SPECIAL_ATTACK_AUDIT.md`.
- Patcher `tools/luna/HungBySonMovePacePatcher.java`: exact inverse on the entity class,
  `MovePace` the only added class (270 classes).
- Checks:
  - MovePaceHostTest: 137 checks, Hung's loop simulated at every speed with 16–80ms frames.
  - HungBySonMovePaceBinaryTest: 11 checks; runs the candidate's actual update prefix.
    Other entities still take 10 of 16 updates; the local player takes 40 steps in
    1120ms at any update rate; the mounted step is unchanged.
  - The full regression set and emulator startup passed.
  - No live-server test.
- Build: `tools/luna/build-hung-move-pace.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`.

## Hung By Son x1: Battlefield static objects and 30ms Special spacing (2026-10-24)

- Published `build/hung-battlefield-static-20261024-a/Hung_By_Son.jar`, SHA-256
  `4842A7CA88C337FB1E9E37DFE45CB85C684458E50B0B1E0148791D0F2ECA1F12`, from
  `8DF9215EDBD99E17ED008568E44D9A724BF871C21F8919029D01B48AF3DAC152`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-battlefield-static-20261024-a.jar`.
  `published.json` confirms other endpoints and golden unchanged. X3 not rebuilt.
- Bug: with Mode chiến trường ON the zone-change sign "Khu" (NPC id -43)
  disappeared. The base entity no-arg constructor ended with `capturePosition(this)`
  while `aZ/ba` were still 0, marking (0,0) as a known server position. NPCs
  (`§liiiilli`) and dropped items (`§liilllli`) are built through it and positioned
  afterwards with no server position packet, so `align()` in their update moved them
  to the map corner. Removed that one capture (exact inverse); the positioned
  constructor and all receive-hook captures remain, so players/monsters still align.
- `SPECIAL_MIN_GAP_MS` 28 → 30 at the user's request; nothing else in the controller.
- Battlefield binary65 (no-arg entity not known; static object keeps its position;
  real item constructor/update with the mode ON stays in place), SpecialPacing6,
  FarmSpotHost17, Farm binary1417 and the full regression set passed; emulator
  startup clean. No live-server test.
- Build: `tools/luna/build-hung-battlefield-static.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`.

## Hung By Son x1: Farm spot stuck policy (2026-10-24)

- Published `build/hung-farm-spot-guard-20261024-a/Hung_By_Son.jar`, SHA-256
  `8DF9215EDBD99E17ED008568E44D9A724BF871C21F8919029D01B48AF3DAC152`, from
  `50558BCB847E7FCE5028CC6258C7BB230EDD06C2BB7EDAF56FB85009C49C20FA`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-farm-spot-guard-20261024-a.jar`.
  `published.json` confirms other endpoints and golden unchanged. X3 not rebuilt.
- Review of every Farm-at-spot exit: the only non-death, non-trip one is Farm's
  400-tick no-attack counter (village scroll). It counts ticks, so it fired after
  ~8s at x1, ~4s at x2, ~2.7s at x3, and it was only reset by a successful send.
  Causes besides UI: no MP with only an out-of-range default attack, out-of-range
  or refused targets, and a pending manual cast keeping the player "busy".
  Auto Bán Rác (bag almost full) and Auto Hồi Sinh "Tự về làng" are features.
- `farmSpot` now owns the decision in real time. Send, waiting on cooldown/MP/600ms
  gate/28ms spacing, an empty spot and a ghost count as progress. Refused sends
  rotate targets (3 refusals → skip 3s). Only after 15s real time without progress
  does the native counter run again (re-entry starts a fresh window). A ghost is
  preferred every tick. A local cast keeping the actor busy for 1.5s is cancelled via
  new bridge `farmSpotCancelCast` (legacy cancel, tag/cast/cA/cG cleanup).
- Changed: controller, `FarmSpotAccess` (+cancel), bridge (+1 method, exact inverse).
  FarmSpotHost17, SpecialPacing6, Farm binary1417 (UI open 450 ticks; refusals keep
  the counter at 0 for ~15s then hand it back; pending cast cancelled after 1.5s and
  sends resume) and the full regression set passed; emulator startup clean.
  No live-server test.
- Build: `tools/luna/build-hung-farm-spot-guard.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`.

## Hung By Son x1: Special 28ms spacing and Farm with UI open (2026-10-24)

- Published `build/hung-special-pacing-20261024-a/Hung_By_Son.jar`, SHA-256
  `50558BCB847E7FCE5028CC6258C7BB230EDD06C2BB7EDAF56FB85009C49C20FA`, from
  `588B9F9370AFB4E3589E7BB3D794DA9E2564383F10B57351515E3E984A18EE0E`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-special-pacing-20261024-a.jar`.
  `published.json` confirms other endpoints and the golden baseline unchanged.
  `Hung_By_Son_x3.jar` is not rebuilt and still lacks these two changes.
- Speed: the Bạch Hổ tempo loop runs 50 (x1) to 150 (x3) updates/s plus catch-up
  bursts; Special picked and sent once per update, so ready skills went out 20ms,
  10ms or ~0ms apart. ModUpLv sends at most once per ≥28ms frame. `chooseSpecial`
  now waits until ≥28ms real time after the previous Special send
  (`SPECIAL_MIN_GAP_MS`). Cooldowns/600ms gate were already real time. See
  [audit](MODUPLV_SPECIAL_ATTACK_AUDIT.md).
- Village return: Farm-origin Special requests required `isWorldReadyStatic()`
  (world screen, no dialog, no text input). With e.g. guild → member list open every
  send was refused, Farm's 400-tick no-attack counter expired (~8s at x1) and used a
  village scroll. The request gate now calls `farmWorldReady()`, a copy of `ready()`
  without those three UI checks (connection, world entry, map, alive player and native
  actor checks kept), matching native Farm casts. Auto đánh keeps pausing on UI.
- Only the bridge (one call renamed, one method added; exact inverse) and
  `AutoAttackController.chooseSpecial` changed; 269 classes.
- SpecialPacingHost6 (incl. x3 update rate), FarmSpecialHost44, FarmSpotHost13,
  Farm binary1002 (450 real Farm ticks with another screen + dialog open: sends
  continue, counter <400, no village scroll), Special52, AutoAttack1805 and the full
  regression set passed; 12s emulator startup empty stderr. No live-server test.
- Build: `tools/luna/build-hung-special-pacing.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`.

## Hung By Son X3: Special + Farm spot from current x1 (2026-10-23)

- Published `build/hung-farm-spot-x3-20261023-a/Hung_By_Son_x3.jar`, SHA-256
  `7FB67D939756D36A032914CFE9A846F88EF10632F3C4E82E3E59DEB525CDC62A`, packed from
  exact x1 `588B9F9370AFB4E3589E7BB3D794DA9E2564383F10B57351515E3E984A18EE0E`
  (class set `AC4499AC…`, 269 classes). Replaces Battlefield X3 `1FB24D59…`;
  verified backup `../jar/backup/Hung_By_Son_x3-before-hung-farm-spot-x3-20261023-a.jar`.
  `published.json` confirms every other endpoint and the golden baseline unchanged.
- X3 now also carries the x1 Special Attack toggle, Farm special/priority, render-only
  quiet pose and Farm-spot standing attack. `HungBySonX3Packer` accepts this later
  Battlefield-profile pin (269 → 830 classes) alongside the old 267 pin.
- Checks: X3 host 1,886 with all 830 classes linked and three-slot parity;
  Battlefield 134 across slots; new Farm spot X3 28 (two slot-local guards, slot
  bridge interface/methods, each slot controller run); crowded cache 74; Auto
  Attack RMS/virtual input; launcher `1 → 2 → 3 → 1` with empty stderr.
  No live-server gameplay test.
- Build: `tools/luna/build-hung-farm-spot-x3.ps1`; publish:
  `tools/luna/publish-hung-farm-spot-x3.ps1`.

## Hung By Son x1: Farm Special stands at its spot (2026-10-23)

- Published `build/hung-farm-spot-20261023-b/Hung_By_Son.jar`, SHA-256
  `588B9F9370AFB4E3589E7BB3D794DA9E2564383F10B57351515E3E984A18EE0E`, from
  `FECE5BDA7ADC9909676EEAD8B0C0BF71821B118E94697432422016FD6D15A4D2`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-farm-spot-20261023-b.jar`.
  `published.json` confirms other endpoints and the golden baseline unchanged.
- Cause found in Farm's at-spot tick: it re-armed the legacy auto (walks back to
  its centre and toward targets) and ran a native hotbar cast loop with full
  animation; with direct sends the local state rarely became "attacking", so
  the 400-tick no-attack counter used a village scroll about every 15s.
- Two guards in `codexFarmUpdate` (stand and move branches, after the native
  map/zone/distance checks and position pin) call `HungAutoAttackAccess.farmSpot`.
  With Farm + Special ON, `AutoAttackController.farmSpot` holds the legacy auto,
  keeps the nearest living Farm-selection monster, sends through the existing
  Special chooser and resets the counter on a send or an empty spot. Ghosts are
  only selected for Farm's native captcha branch. Special OFF/Auto đánh ON return
  `false` and run the original code. Exact Farm/bridge inverse; 4 classes changed,
  `luna/api/FarmSpotAccess` added; 269 classes, 779 unchanged resources.
- FarmSpotHost13 and Farm binary550 (450 real `Farm.update` ticks over 22.5s:
  ~600ms sends, no path/movement/animation, counter <400; death, map mismatch
  and Special OFF paths) plus full Special/AutoAttack/battlefield/timing/cache/
  EXP/offline regressions passed; 12s emulator startup had empty stderr.
  No live-server/account gameplay test was performed.
- Build: `tools/luna/build-hung-farm-spot.ps1` (needs `rg` on PATH); publisher:
  `tools/luna/publish-hung-special-attack.ps1`. [Behavior](HUNG_BY_SON_AUTO_ATTACK.md).

## Hung By Son x1: recover Farm movement and cast lifecycle (2026-10-23)

- Published `build/hung-farm-quiet-recovery-20261023-c/Hung_By_Son.jar`, SHA-256
  `FECE5BDA7ADC9909676EEAD8B0C0BF71821B118E94697432422016FD6D15A4D2`, replaces
  the broken quiet release `0FA01EA3F54230DF6A7529C61C55996BDC689B694450474220893494ACEF6187`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-farm-quiet-recovery-20261023-c.jar`.
  Atomic replacement and read-back passed; `published.json` confirms other
  endpoints, including X3 and ModUpLv, and the golden baseline unchanged.
- The prior patch cleared movement paths/destinations/velocities and cast state
  at every native player update. Its initializer/helper/animation early returns
  also skipped native cast lifecycle work. These hooks can interrupt Farm return
  routes and casts. Remove them entirely: native player, cast and animator class
  payloads match working baseline `826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4`
  byte for byte, and all existing bridge methods match that baseline.
- Hide the local idle/attack pose via 79 read substitutions in five graphics
  methods and eight pure atlas helpers; no simulation fields are written by the
  new accessors. Auxiliary cast layers use the native negative-frame skip.
  Walking/death/remote rendering stays native; automatic Farm travel and its
  manual movement restriction retain native handling. Special sends and priority
  logic are unchanged. Five classes differ from the withdrawn quiet JAR;
  268 classes and 779 unchanged non-class entries remain.
- Farm native84 covers actual sprite/overlay rendering helpers, preserved
  route/destination/velocity/cast data, native return walking after simulated
  revival, real local cast initialization/timing and a native callback packet
  while its pose is hidden. Forty special sends with native update cycles span
  23,439ms of simulated controller time, beyond the reported 15s failure window.
  Existing Special54/52, Farm host44, AutoAttack1805, battlefield61, native
  timing2605, remote animation477, cache/recovery and EXP/RMS/UI suites passed.
  All 268 classes linked; twelve-second emulator startup had empty stderr.
  No live-server/account gameplay test was performed.
- Build: `tools/luna/build-hung-farm-quiet-recovery.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`, expected input hash above.
  Rebuild from the withdrawn quiet backup with a fresh BuildTag.
  [Behavior](HUNG_BY_SON_AUTO_ATTACK.md).

## Hung By Son x1: idle avatar during Farm special attack (2026-10-23, withdrawn)

Withdrawn after the user reported that Farm stopped attacking and remained in
the village. The render-only recovery above supersedes these global idle hooks.

- Published `build/hung-farm-quiet-20261023-a/Hung_By_Son.jar`, SHA-256
  `0FA01EA3F54230DF6A7529C61C55996BDC689B694450474220893494ACEF6187`, from
  `826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-farm-quiet-20261023-a.jar`.
  Atomic replacement and read-back passed; `published.json` confirms all other
  endpoints, including X3 and ModUpLv, and the golden baseline unchanged.
- With Farm and Special ON, the living local avatar stays idle: clear walking
  path/destination and cast/frame state before and after native player update.
  Suppress receive-side visual cast initialization, native fallback animation
  and the local animation driver. Direct special sends, skill priority and
  cooldown/MP bookkeeping continue; native HP/MP/damage updates still run.
  Remote actors and death state are excluded. Disabling either mode restores
  native animation handling.
- Exactly four existing classes changed; 268 classes and all 779 non-class
  entries retained. Insertion inverse, untouched native members, class versions,
  resource/manifest identity, CLDC checks and all-class linkage passed.
  Farm native32 includes actual sends, strict 600/601ms, priority reservation,
  idle/path cleanup, receive visual echo, native animation driver, remote/death
  isolation and Special OFF behavior.
- Full existing semantic/native regression passed, including Special host54/
  native52, Farm host44, AutoAttack1805, battlefield61, native timing2605 and
  remote animation477. Twelve-second headless emulator startup had empty stderr.
  No live server/account gameplay test was performed.
- Build: `tools/luna/build-hung-farm-quiet.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`. Rebuild with the preceding backup
  as `-InputJar` and a fresh BuildTag. [Behavior](HUNG_BY_SON_AUTO_ATTACK.md).

## Hung By Son x1: Farm special attack and priority reservation (2026-10-23)

- Published `build/hung-farm-special-attack-20261023-a/Hung_By_Son.jar`, SHA-256
  `826E8FE3BCE4D08541A206BDA7C91DCC01CF06BFC6B956D744BD1F36FBC26EA4`, from
  the preceding x1 `05F16E80580F2941F7DC8A6092BCA5A108DFD82D3CF94E930E436251B1006119`.
  Verified backup: `../jar/backup/Hung_By_Son-before-hung-farm-special-attack-20261023-a.jar`.
  Atomic replacement/read-back passed; `published.json` proves other endpoints,
  including X3 and ModUpLv, and the golden baseline unchanged.
- Farm now uses the same direct-send mode when its own toggle and Special are
  ON. Scope requires native Farm enabled, Auto Attack master OFF, the exact
  initiating thread and local actor. Existing Farm target/filter/path/range and
  current hotbar remain authoritative. Farm's ordinary cadence gate is bypassed
  only in this mode. Manual/remote requests retain their normal path.
- Both owners use semantic priority over the native enabled hotbar cells:
  highest native-eligible skill ID first, default attack last. Readiness is
  checked before the shared strict 600ms gate. A gated winner reserves the
  next send, so lower skills do not refresh the clock and postpone it for
  several additional seconds. This matches ModUpLv's descending/default list
  ordering and whole-selector return on a blocked gate, while retaining Hung's
  configured skill subset instead of importing all learned skills.
- Farm dispatch holds the existing controller monitor and enters/exits the
  provenance scope in finally. Direct cast cancellation changes marked Farm
  tag 0 to -1 before native cleanup; later callbacks cannot send again. Mode
  transitions cancel marked local pending Farm casts. The native Farm cursor
  records the actual sent slot, or stays unchanged if the winner waits.
  Incomplete skill records are skipped until their data is ready.
- Exactly five existing classes changed, one semantic access interface added;
  268 classes and 779 unchanged non-class entries. Pinned-input inverse and
  unchanged native members, class versions, CLDC screen, resource/manifest
  identity and all-class linkage passed. Farm host44/native24 includes valid
  ready skill0+7, actual native Farm wrapper/opcode9, strict 600/601ms behavior,
  no fallback, callback cancellation, manual/remote isolation and Auto priority.
- Existing Special host54/native52 (actual opcode6/9), AutoAttack1805/input24,
  battlefield61, PK corpse11, cache/stale/selection/crowded/recovery, native
  timing2605/remote animation477, offline/enhancement/trade and EXP host/native/
  RMS/UI suites passed. Twelve-second isolated emulator startup had empty stderr.
  No live server/account benchmark or gameplay test was performed.
- Build: `tools/luna/build-hung-farm-special-attack.ps1`; publish with
  `tools/luna/publish-hung-special-attack.ps1` and expected input hash above.
  Rebuild via `-InputJar` using the preceding backup and a fresh BuildTag.
  [Behavior](HUNG_BY_SON_AUTO_ATTACK.md),
  [priority audit](MODUPLV_SPECIAL_ATTACK_AUDIT.md).

## Hung By Son x1: optional special attack (2026-10-23)

- Published `build/hung-special-attack-20261023-b/Hung_By_Son.jar`, SHA-256
  `05F16E80580F2941F7DC8A6092BCA5A108DFD82D3CF94E930E436251B1006119`, from
  the exact preceding x1 `DA429E64940D87852F212854AF793C992D99DDF039DED0B85CAD1747FD950646`.
  Backup: `../jar/backup/Hung_By_Son-before-hung-special-attack-20261023-b.jar`.
  Atomic replacement and read-back hashes passed; `published.json` confirms
  all other endpoints, including X3 and ModUpLv, and golden unchanged.
- Sixth Auto đánh row **Tấn công đặc biệt** is session-only and OFF by default.
  It modifies the existing master Auto đánh; toggling it does not enable the
  master or change Farm. ON retains Hung's native hotbar/skill policy, primary
  target/range, fanout, PK and battlefield filtering, but sends synchronously
  at the prepared-cast boundary. Native serializers and cooldown/MP bookkeeping
  are reused. Cancellation rejects later animation callbacks. OFF restores
  the previous cast/animation/configured delay and 100ms movement-resume behavior.
- Donor timing: configured Hung Delay and native skill interval do not gate
  special sends. Only IDs 7/8/19/20 require strictly more than 600ms since any
  special-mode send; other IDs retain cooldown/MP eligibility without that gate.
  The donor's 20-target overwrite and 20,000,000 range are not imported.
- Exactly five existing classes changed; 267 classes and 779 non-class entries
  retained. Pinned-input insertion inverse, untouched native members/support
  methods, resource/manifest identity, CLDC support screen and linkage passed.
  Special semantic54/native52 includes actual opcode6 and9, native fanout,
  MP/cooldown, no duplicate callback, future native request timestamp/interval
  bypass, strict 600ms eligibility, movement, PK and dead targets.
- Existing AutoAttack1805, native keyboard/touch input, battlefield61, PK corpse11,
  cache254/stale99/selection46/crowded45044/native27/recovery36, remote animation477,
  native timing2605, tempo94, noclip24/349, offline94, enhancement13/NPC17/trade,
  semantic/controller/route/combat/material and EXP host/native/UI/RMS suites
  passed. Twelve-second isolated emulator startup had empty stderr; HUD image
  reviewed. No live-account/server gameplay tested.
- Build: `tools/luna/build-hung-special-attack.ps1`; publisher:
  `tools/luna/publish-hung-special-attack.ps1`. Rebuild from the preceding
  backup via `-InputJar`; output must use a fresh BuildTag. Publication receipt,
  verified hash, catalogs and test logs are in the final stage.
  [Usage and behavior](HUNG_BY_SON_AUTO_ATTACK.md).

## Hung By Son: battlefield mode and Xà nữ cache exclusion (2026-10-23)

- Published X1/X3 from `build/hung-battlefield-20261023-final-f/` and
  `build/hung-battlefield-x3-20261023-final/`; receipts `verified.json` and
  `published.json` bind the tested and released bytes. Backups before replacement:
  `../jar/backup/Hung_By_Son-before-battlefield-20261023-075457-882.jar`
  (source `3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF`)
  and `../jar/backup/Hung_By_Son_x3-before-battlefield-20261023-075457-882.jar`
  (source `9A91554743E22884DC7DA5C660B668D69F199C7ED632683C58EDA51E97B79F57`).
- New fifth row **Mode chiến trường** in Auto đánh is OFF at startup and does
  not persist. Seven target-coordinate and eleven direct-position receive hooks
  preserve the last server position. Native entity update plus sixteen subclass
  overrides align remote objects before/after their update; original receiver,
  animation and update instructions survive an insertion inverse. Local player
  movement is bypassed. OFF retains native movement.
- Player combat receiver observer reads the local attacker ID, positive damage,
  positive pre-event target HP and post-event HP zero. It validates the entire
  frame before committing names and resets the stream. Only known players with
  level other than 40 enter the unique ten-name FIFO; Xà nữ, self, monsters,
  unknown/incomplete targets and malformed events are excluded. Duplicate names
  keep their original order; map/zone changes retain FIFO; disabling/closing
  clears it. Auto PK selection/pending-request validation rejects these names;
  the two native overhead-name paths select the existing bold red font.
- Cache excludes Xà nữ on every map independent of the toggle, including old
  persisted data, delayed template catalogs and cached-ID replacements. Restore
  and detail paths guard against it; bounded active-context rescans discover
  delayed names. Purges remain server-scoped, preserve unrelated templates/native
  live objects and retain the complete map48 bypass.
- X1 checks: Battlefield semantic21/native61; 267 classes linked under
  `-Xverify:all`; AutoAttack170, actual native keyboard/touch24, PK corpse11,
  cache254, stale99, selection46, crowded45,044/native27, recovery36,
  remote-animation477, native timing2,597, tempo94, Noclip24/349, offline94,
  enhancement13/NPC17, trade and five semantic/controller/route/combat/material
  suites. EXP38,735, interpolation4,548, info15, native EXP133/info11/modal,
  persistent RMS and isolated twelve-second startup passed. Existing HUD/notice
  PNGs were visually reviewed. Runtime fixtures use synthetic packets and
  isolated worlds; update-hook coverage plus executable alignment is verified
  without simulating a fully logged-in native player update.
- X3 checks: all824 classes linked, host1,878, Battlefield134 across all three
  slots, crowded-cache74, AutoAttack299 including RMS/virtual input, launcher
  `1 → 2 → 3 → 1` with empty stderr. Mode/FIFO/snapshot statics remain per slot;
  shared EXP backend, wrapper and resources retain their established boundary.
- Publisher verified both old backups and new release hashes, all other endpoint
  JARs and the golden baseline. No live account/server gameplay or battlefield
  score-credit behavior was tested. See [behavior](HUNG_BY_SON_BATTLEFIELD.md)
  and `symbols/hung-battlefield-20261023.properties`.

## Hung By Son X3: current x1 EXP display and info dialog (2026-10-19)

- Published `build/hung-exp-info-x3-20261019-b/Hung_By_Son_x3.jar`, SHA-256 `9A91554743E22884DC7DA5C660B668D69F199C7ED632683C58EDA51E97B79F57`, 3,069,306 bytes. Source is exactly the approved x1 `3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF`; template is the existing pinned launcher. Backup `../jar/backup/Hung_By_Son_x3-before-hung-exp-info-x3-20261019-b.jar` matches previous X3 `4997CF94549554D4A2E70EF50692659A000D0C0D6413A13360D3625077F12796`. Atomic replacement, release read-back and backup hashes pass; `publish.json` proves x1, other endpoints and golden unchanged.
- All three namespaces receive the same three-digit percentage, supplied 12×12 icon, grouped whole EXP values, Vietnamese waiting text and pointer-only info modal as x1. Hourly sampling, interpolation phase, image cache and modal ownership remain per slot. One byte-identical root EXP level backend stays outside slot reset. The 19 wrapper classes, launcher icon and all source non-manifest resources remain byte-identical; slot RMS aliases and source gameplay behavior are preserved. No compact item menu is reintroduced.
- Full semantic/controller/route/combat/login/Auto Attack suites and EXP host suites pass. X3 host passes 1,875 checks with all 812 class links and complete namespace parity; crowded cache 74, actual Auto Attack/input/two-process RMS 299, native EXP/packet/HUD/shared File RMS 367. Launcher keeps three live clients through held-* and `1 -> 2 -> 3 -> 1`. The new EXP info fixture passes 28 host and 2,293 worker checks: all three actual pointer opens/confirmations, Enter/fire/softkeys blocked, 2.2 seconds of real Farm updates per modal, outside/drag cancellation, 12px/native-A size and cached pixel scaling, independent rates, and scoped Static clear with other modals/rates/shared EXP preserved. Runtime logs contain no errors; class maximum is Java 7 and all 48 scoped support classes pass the CLDC API screen. HUD/icon/modal screenshots reviewed; no account/server gameplay tested.
- Build: `tools/luna/build-hung-exp-info-x3.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>`; publish: `tools/luna/publish-hung-exp-info-x3.ps1 -StageBuild <verified-stage>`. Packer retains the historical source contracts and adds an exact hash/class-set contract for the approved SVG x1. Final stage contains bound catalogs, archive/CLDC proof, test logs, screenshots, `verified.json` and `publish.json`. [EXP info behavior](HUNG_BY_SON_EXP_INFO.md).

## Hung By Son x1: supplied 12px SVG info icon (2026-10-19)

- Published `build/hung-exp-svg-20261019-b/Hung_By_Son.jar`, SHA-256 `3CB0DBA1EC67F0687057AEB1533C025A7858D85DC11B2A246097A029C053FCCF`, from exact prior x1 `3E09C037BBE7144A475228477980C36D92336F7D8BC05A34ED848120A6942299`. Backup: `../jar/backup/Hung_By_Son-before-hung-exp-svg-20261019-b.jar`, exact old hash verified. Atomic replacement resumed successfully after the user closed the emulator. Release read-back and backup hashes pass; `publish.json` proves other endpoints and golden unchanged.
- Uses the user's replacement `C:\Users\BUI THE THUAT\Downloads\icon-info (1).svg`, intrinsically12×12 with viewBox15×15. The source was copied byte-identically to `src/luna/ui/icons/exp-info.svg`, SHA-256 `CCE425DE74BB190D4B8BBF86CA43119FE7EEDD941929C4E404B111C18CF9F0CB`. Its exact three paths/colors/crispEdges are rendered into the existing PNG resource; output12×12 equals the decoded native A image dimensions. Center remains normal x103/y52, compact x91/y51; draw/hit top-left becomes97/46 or85/45 with a12×12 hitbox. Source dimensions are validated against A, rather than altering the supplied SVG.
- Only `luna/ui/exp-info.png` and six draw/hit constants in the existing UI bridge change. Complete method inverse passes,262 other classes and778 other non-class entries remain byte-identical. Archive remains1,042 entries/263classes; native HUD, modal guards, sampling, dot grouping, rate/ETA and all other endpoints are untouched.
- Full semantic/controller/route/Auto Combat/Login Material/Auto Attack suites and existing EXP/fraction/rate/info host suites pass. Native EXP133 with263links, RMS cleanup/two-process persistence, native UI and68-check Farm/ENTER/modal suite pass under `-Xverify:all`; startup12s has empty stderr. Additional image assertions verify exact equality with native A width/height, caching and2× transparent pixel scaling. Normal/compact aligned HUD PNGs and modal PNGs were reviewed; no live account gameplay tested. The interrupted stage-a was not published; final-b uses exactly the later file requested by the user.
- Rebuild: `tools/luna/build-hung-exp-svg.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>`; publish with `tools/luna/publish-hung-exp-svg.ps1 -StageBuild <verified-stage>`. Renderer, archive boundary, source hash, symbols, tests, screenshots and publication records are in the final stage. [Behavior/source](HUNG_BY_SON_EXP_INFO.md).

## Hung By Son x1: custom info icon, grouped numbers and pointer-only close (2026-10-19)

- Published `build/hung-exp-info-polish-20261019-b/Hung_By_Son.jar`, SHA-256 `3E09C037BBE7144A475228477980C36D92336F7D8BC05A34ED848120A6942299`, from exactly the prior info x1 `1842971A980464F294152E9AE1903A828B681BE8ED54757A1B1DF6F510135704`. Backup: `../jar/backup/Hung_By_Son-before-hung-exp-info-polish-20261019-b.jar`. Atomic replacement/read-back and backup hashes pass; X3, other endpoints and golden are unchanged in `publish.json`.
- Replaced the cut-out A rim/font glyph with a separately drawn transparent 14×14 PNG: gold border, blue fill, clear ivory `i`. Its center follows the actual native A column (normal x103, compact x91). The previous icon added the panel's x/y origin to absolute status-column coordinates, shifting it away from A; new draw/hit coordinates are absolute and verified with both layouts and a nonzero panel origin. The native HUD/A class itself remains byte-identical. Source art: `src/luna/ui/icons/exp-info.png`, reproducible pixel map `tools/luna/GenerateExpInfoIcon.java`. `ExpInfoIcon` caches the image and nearest-neighbor scale; no font resource changes.
- Whole EXP values in the dialog now use dot grouping: `12.000`, `100.000.000`, up to `9.223.372.036.854.775.807`. Missing text, zero values, hourly threshold, rounding/ETA arithmetic, compact outside K/M and native percentage format retain prior behavior.
- Fixed automatic modal disappearance: two native Farm updaters directly cleared `activeDialog`; the native key/close routes also accepted ENTER. `ExpInfoModalGuard` owns only the exact EXP dialog/current world/current player. It filters those two Farm clear sites and blocks native key polling, key callbacks and generic close commands for that owned dialog. Only a real pointer press/release inside its own confirmation button grants a one-shot close permission; outside taps/drag cancellation do not close it. Foreign dialogs and leaving-world lifecycle cleanup retain native behavior; no dialog is reopened after logout/connection wait.
- Archive boundary: 1,042 entries, 263 classes; six existing class payload changes (UI bridge, rate formatting, native engine, native dialog and two Farm modules), two added classes (`ExpInfoIcon`, generated modal guard) and one PNG. All 778 prior non-class entries and all unrelated classes remain byte-identical, including the original HUD and manifest. Native class versions are preserved; added helper is major51 and guard major48. EXP arithmetic methods remain canonical; the native modal helper proves inverse equality of all 99 methods in its five touched classes.
- Full candidate stage passes semantic/controller/route/Auto Combat/Login Material, Auto Attack170, EXP38735, fraction4548/integration2312, rate+BigInteger oracle1554 and info lifecycle15 checks under `-Xverify:all`. Existing native EXP133/263class linkage, login RMS cleanup/two-process File RMS and startup12s empty stderr pass. UI tests exercise both aligned layouts, origin independence, image caching and exact 2× transparent pixel scaling, dot-grouped modal text and pointer confirmation. New modal guard suite passes68 native checks: more than2.2s of real Farm updates with countdown proof that the old null-clear branch executes, repeated ENTER/fire/softkeys and native key polling, outside/drag handling, one-shot pointer close, foreign dialogs and world lifecycle. No background exceptions remain. HUD/modal screenshots in both `HungBySonExpInfoBinaryTest/` and `ui-final-layouts/` were visually reviewed. No live account gameplay was tested after this repair.
- Build/publish uses `build-hung-exp-info-polish.ps1` and `publish-hung-exp-info-polish.ps1`; the pinned preimage catalog is `symbols/hung-exp-info-polish-base-single-20261019.properties`. Stage `-a` was not published; final `-b` produces the same candidate bytes with the complete modal regression suite. [Current behavior and source](HUNG_BY_SON_EXP_INFO.md).

## Hung By Son x1: EXP information icon and Vietnamese calculating text (2026-10-19)

- Published `build/hung-exp-info-20261019-c/Hung_By_Son.jar`, SHA-256 `1842971A980464F294152E9AE1903A828B681BE8ED54757A1B1DF6F510135704`, from exactly the current x1 percentage release `38CBF77190184F479645EF44E9EA74898C19ACB0227C16E245DB6834D83877DE`. Backup: `../jar/backup/Hung_By_Son-before-hung-exp-info-20261019-c.jar`. `publish.json` verifies atomic replacement/read-back, exact backup and unchanged X3/other endpoints/golden.
- The new lowercase `i` reuses only the existing A sprite's circular rim and a native yellow glyph. It sits below A beside the EXP line, follows both native HUD layouts and graphics touch scaling, and captures press/drag/release once. Leaving its bounds cancels the gesture. Native menu, modal and connection-wait guards prevent stealing other UI taps. Opening creates a native message dialog with a dialog-bound `Xác nhận` close command; original HUD font, plus/decimal separator, frame, XP bar and K/M line calculation are unchanged. Only the old calculating literal changes to `Đang tính toán...` in support and both bridge fallback sites.
- The dialog independently shows rounded whole-number EXP/hour after at least 300000ms from the first positive self-character gain, the rounded whole level total only when the HUD estimate is available, and remaining-level ETA only with both total and a positive rate. ETA uses remaining progress rather than the full total and rounds positive fractional minutes upward. Missing fields individually show the Vietnamese calculating text. Rate survives level changes/estimator segment resets; actual disconnect/identity changes reset it, and no hourly/offline state is stored in RMS. [Details and limitations](HUNG_BY_SON_EXP_INFO.md).
- Final archive: 1,039 entries, 261 linked classes, three existing class payload changes (native engine pointer callbacks, EXP bridge, semantic EXP support) and two additions (`ExpInfoBridge`, `ExpRateSupport`). All 778 original non-class entries, manifest, native HUD class and unrelated classes are byte-identical. Existing class versions are preserved; UI addition is major 48, rate helper major 51 and new constant pools pass the CLDC screen. Native engine/bridge inverse proofs and support-method canonical checks pass.
- Exact candidate tests pass under `-Xverify:all`: semantic/controller/route/Auto Combat/Login Material, Auto Attack 170, existing EXP 38,735, fraction helper 4,548, fraction integration 2,312, rate/ETA BigInteger oracle 1,543, and EXP info lifecycle 15 checks. Existing native EXP suite passes 133 host checks with real packet30/RMS cleanup/fresh-process File RMS; all 261 classes link. The new isolated native UI suite passes its host/worker assertions for scaled hit bounds, actual press/release open, drag cancellation, repeat release, unrelated input, menu/wait blocking, native close back to the existing world, known/unknown rate text and Vietnamese glyphs. Portrait 240x320 and landscape 320x240 dialog bounds and screenshots pass visual review. Isolated startup runs 12 seconds with empty stderr. No account/server gameplay was tested.
- Build/publish: `tools/luna/build-hung-exp-info.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>` then `tools/luna/publish-hung-exp-info.ps1 -StageBuild <verified-stage>`. Source: `ExpRateSupport`, `ExpEstimateSupport`; native UI generation: `HungBySonExpInfoUiBuilder`; semantic merge/archive verification: `HungBySonExpInfoPack`. Stage contains full logs, `info-symbols.properties`, archive proof, `verified.json`, native HUD/modal PNGs and `publish.json`. Earlier failed/staging attempts were never published.

## Hung By Son x1: three fractional EXP percentage digits (2026-10-19)

- Published `build/hung-exp-percent-20261019-c/Hung_By_Son.jar`, SHA-256 `38CBF77190184F479645EF44E9EA74898C19ACB0227C16E245DB6834D83877DE`. Exact source/preimage is BCF304 from the current rolled-back x1. Backup: `../jar/backup/Hung_By_Son-before-hung-exp-percent-20261019-c.jar`. Atomic replacement/read-back and backup hashes pass; `publish.json` proves X3, other endpoints and golden unchanged.
- Only the native HUD fraction expression changes: e.g. `Lv.70 +37,4%` becomes `Lv.70 +37,461%`. Per the user's explicit constraint, native font, plus sign, decimal separator, coordinates, frame, XP bar and bottom K/M line retain their original format/calculation. The longer text is not compensated by a font or layout change. Unknown total/phase stays at native one-digit precision until clean live samples are available; this is approximate display resolution, not guaranteed 0.001-percent server accuracy.
- The helper intersects coarse-progress phase bounds with cumulative EXP gains and preserves phase constraints across the 64-sample window while the total estimate is stable. Loss/reset, level/identity changes and disconnect discard phase; the existing total-by-level RMS format is unchanged. Positive clean progress is bounded by the latest native 0.1-percent bucket and does not move backwards. No per-frame RMS or native EXP-field writes are added.
- Exact archive boundary: three existing class payload changes (HUD, EXP support and its canonically unchanged record class), one added `luna/ui/ExpPercentInterpolator`; 1,037 entries and 259 linked classes. Whole native HUD inverse proof preserves every other instruction/method; resources/manifest and native class version 48 remain unchanged. Replacement support/helper classes are Java 7, screened for forbidden CLDC references; existing support methods outside the explicit lifecycle/cache allow-list remain canonical.
- Candidate-embedded EXP host checks 38,735, interpolator checks 4,548 and lifecycle/cache integration checks 2,312 pass under `-Xverify:all`, plus semantic/controller/route/Auto Combat/Login Material suites and Auto Attack 170 checks. Native EXP fixture passes 133 host checks, actual tiny self-character packet gains update the fraction inside an unchanged native bucket, and fresh-process File RMS write/read plus native cleanup pass. Isolated startup runs 12 seconds with empty stderr. Login/HUD/notice images, including `screenshots/native-hud-slot-0-fine-percent.png`, were visually reviewed. No account/server gameplay was tested.
- Build: `tools/luna/build-hung-exp-percent.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>`; publish reviewed stage with `tools/luna/publish-hung-exp-percent.ps1 -StageBuild <stage>`. Source catalog `symbols/hung-exp-percent-base-single-20261019.properties` binds the prior verified EXP metadata to BCF304; the patcher exact-matches the pinned native expression. Final patch/catalog/check logs and `verified.json`/`publish.json` are in the stage. Failed staging attempts `-a`/`-b` were never published. [Behavior and limits](HUNG_BY_SON_EXP_APPROX.md).

## Hung By Son single + X3: compact menu rolled back (2026-10-19)

- User rejected the compact menu and requested rollback. Both endpoint JARs were atomically restored from the exact pre-change backups: single BCF304 and X3 4997CF. Read-back hashes match those original bytes. Receipt: `build/hung-compact-menu-rollback-20261019-a/rollback.json`; unrelated endpoints and golden are unchanged. No rebuild was needed.
- Rejected feature source, tooling, fixture and catalogs were moved out of the active source tree to `build/hung-compact-menu-rollback-20261019-a/source-rejected/`. The following compact-menu release details are historical and do not describe the current release.

- Published `build/hung-compact-menu-20261019-e/`: single `CE6C54021CBFB0A528EA9A3A968C42A58AD0A65B3D076BF472544E5E4601F803` / 1,339,779 bytes and X3 `9E929CF9B308DBB638F6337A8E6527438F042692853A856F5E20936825C62EC7` / 3,046,616 bytes. Exact preimages are BCF304 and 4997CF from the 14/10 release. Backups: `../jar/backup/Hung_By_Son-before-hung-compact-menu-20261019-e.jar` and `../jar/backup/Hung_By_Son_x3-before-hung-compact-menu-20261019-e.jar`. `publish.json` proves atomic replacement/read-back, exact old bytes, unchanged unrelated endpoints and golden.
- Native item action buttons retain their captions and callbacks, at half width/height with the native skin and Vietnamese text raster downsampled 2x. The existing two-column/centered-final-row cluster compresses toward its former bottom-right edge; description reservation halves. Touch uses the visible compact rectangle rather than the old 5px expansion. Per-command normal/pressed images are cached outside steady painting; relayout and label/zoom changes refresh them.
- Native command configuration clears the compact marker so reused commands return to ordinary menu layout. Successful item-popup creation then applies compact geometry, and its post-action list replacement recalculates reserve/layout as well. Reopening items does not retain old full-size coordinates or shrink repeatedly. Only command/bag classes change per namespace; native callback/packet bodies remain unchanged under a complete structural inverse.
- Single: 260 classes linked, 133 introduced member references resolved, 778 resource/manifest entries unchanged. Menu geometry/native font rendering/cache/touch/reopen/post-action fixture passes153 at 450x240 and240x320; native Auto/combat/inventory/RMS/HUD1803, local services94, enhancement13/NPC17, Auto semantic170 and the five semantic/controller/route/combat/material suites pass. X3:803 links, menu459 across all three actual virtual clients, full namespace/lifecycle/RMS/cache parity1862, crowded74 and held-* launcher1→2→3→1 with the same instances/live loops pass. Final native-button screenshots were visually inspected. Item descriptions in the screenshots are synthetic; live account/server gameplay is untested.
- X3 patches the exact existing three namespaces, adds two stateless helper classes per slot, preserves all wrapper/Static/RMS/shared-EXP bytes, and changes only the manifest's single-source hash. Complete slot parity passes. Old native class versions are preserved; additions are Java7/MIDP with no StringBuilder dependency. Catalogs: `symbols/hung-compact-menu-single-20261019.properties`, `symbols/hung-compact-menu-x3-20261019.properties`.
- Rejected build/publisher sources are archived under the rollback's `source-rejected/tools/luna/`, not part of the active release workflow. Historical design: [compact menu behavior](HUNG_BY_SON_COMPACT_ITEM_MENU.md). Earlier stages a–d were not published.

## HSO MAX: legacy appearance packet alignment (2026-10-17)

- Published corrected `../jar/max_final.jar`, 1,710,858 bytes, SHA-256 `F2E4182D672DAC9F2BBD5992AF99584D1958AC37EC2230F8870B354E475C1239`. Previous `F66EE6FA7DCB998704348B4FC60A04C468CA5F9FB759770180A23A602E9D4DE8` is saved as `../jar/backup/max_final-before-max-character-fix-release-20261017.jar`. Source CHIBI, MAX reference, other endpoint releases and golden retain their bytes. Endpoint, login contract, identity and MAX RMS namespace are unchanged.
- User reported naked/missing equipment both in the world and the equipment panel; the reference screenshot was from `HsoMaxMod.jar`. A narrow receive-side comparison found a definite legacy wire-width mismatch: donor opcode 3 `eq.b(Leo;)V`, opcode 5 `eq.e(Leo;)V`, and opcode 15 `eq.j(Leo;)V` read one signed byte for each element assigned to `ez.dF:[B`. The source receiver reads shorts into its internal `short[]` instead. A nonempty count consumes one extra byte per element, shifting the remaining body/equipment fields and potentially throwing EOF. In self initialization, this includes ten subsequent native part IDs. Header/options, two four-short stat rows and two 21-byte skill rows match the donor. Opcode 13 character-list reads also match.
- `MaxLegacyProtocolPatcher` changes only those three `DataInputStream.readShort()S` calls to `readByte()B`; internal short arrays and all native rendering logic remain intact. Compared with the initial MAX release, only the native receiver class changes; manifest/resources and every other class are byte-identical. The patcher pins both input and donor hashes, verifies the donor readers and asserts that all three receiver assignments are covered. The symbol/report additions record the exact owner/member/descriptor and wire profile.
- `MaxCharacterProtocolTest` passes 1,355 checks: it extracts and executes the actual donor, broken and corrected array loops for opcodes 3/5/15, covers 15 cases including zero count and signed `-1` sentinels, reproduces 12 nonempty failures in the old build, and verifies adjacent data alignment. A complete native self tail preserves the world flag, costume flag and all ten part IDs with the exact donor values; the old reader fails that same fixture. This tests packet consumption and native assignments, not rendered live gameplay.
- Existing checks pass again: five full donor login packets, 4,325 archive/server/storage checks, all 217 classes linked, 133 symbols, unchanged 729 non-manifest resources, semantic/controller/route/Auto Combat/Login Material/initialized legacy route regressions, and 12-second isolated Headless startup with empty stderr. Build/publication evidence: `build/max-character-fix-release-20261017/verified.json`, `character-protocol-test.txt`, `legacy-protocol-patch.txt`, other validation logs and `emulator/`. The final atomic replace resumed after fixing PowerShell's null-string binding for the optional `File.Replace` backup argument; the old release remained intact until the successful replacement.
- Inventory comparison found the category-3 item record widths identical. The original donor additionally has a custom `Mod.onInventory` category reconciliation wrapper; that wrapper is not transplanted without a demonstrated inventory update failure. The patch fixes the established appearance alignment defect. Actual server login, visual recovery of the equipment panel and gameplay after this fix still require a fresh client session.
- Rebuild uses the same `tools/luna/build-max-final.ps1`: generate the pinned endpoint/login base, apply `MaxLegacyProtocolPatcher`, run both packet fixtures and the existing checks, then publish with an exact preimage check and backup. Maintained fixture is `fixtures/binary/MaxCharacterProtocolTest.java`.

## HSO MAX from CHIBI Final (2026-10-17, initial build)

- Published `../jar/max_final.jar`, 1,710,855 bytes, SHA-256 `F66EE6FA7DCB998704348B4FC60A04C468CA5F9FB759770180A23A602E9D4DE8`. Source is exactly `../jar/chibi_final.jar`, SHA-256 `A5C8FC9C5659518E2E62AB702381FD4E781B3C55ABE82F87EA4FF63A0D142DFB`; reference is `../jar/HsoMaxMod.jar`, SHA-256 `B46D256F4304FA36566CB5B4DE4882A15E99069B27CBAD82B217EED254B41BC6`. This creates a new endpoint release; neither input nor any existing release or golden baseline is replaced.
- Donor evidence: `dw.<clinit>()V` supplies `HsoMax` / `sv.hsomax.com`; `ft.o()V` selects TCP port 19129. Both native tables now expose one MAX row. All five table assignments use `maxOnlyServer`, all twelve writes to the selected-server byte normalize to zero, and the actual socket constructor forces `sv.hsomax.com:19129` even for alternate dial paths. All five plaintext CHIBI host uses and its XOR-encoded host are repointed.
- Shared serializer follows donor `q.a(String,String,String,String,String,String,int,byte)V`: opcode 1; raw username/password; UTF version `2.5.8`; three `TUOITHO_SECRET_KEY_2026` extension strings; original selector/device ordering; donor-specific byte 80 and short 2389; final device-info UTF. The source's `|HSOR_SECRET_2026` suffix and eighth `LOCK` UTF are removed. Donor byte 80 / short 2389 are login payload values, distinct from the TCP port 19129. Manual, automatic and replayed source login paths continue through the one native serializer.
- MIDlet name/vendor/label and implementation title/vendor use `HSO MAX`; native entrypoint, icon, MIDlet version 4.0.2 and pickup metadata remain. All four direct RMS calls use maintained `luna.core.MaxRecordStores`, with prefix `HSO_MAX_`, scoped list/delete and idempotent prefix handling. Existing CHIBI/other-suite account records remain untouched. The native account codec executes a fresh save/load roundtrip in the storage fixture.
- `MaxVariantBinaryTest` passes 4,326 checks under `-Xverify:all`: five complete donor/candidate plaintext packet comparisons with synthetic ASCII/Unicode credentials, varied selector/device values and deliberately incorrect caller version/keys; actual socket-URI constructor execution; singleton/selector/RMS boundary checks; ZIP CRC; identity; original class versions; 217 class links; all 729 non-manifest resources byte-identical. Ten source classes change and one RMS helper is added; the other 206 classes are byte-identical. All 133 binary catalog symbols match the published artifact. Semantic/controller/route/Auto Combat/Login Material and initialized legacy route regressions pass. Isolated memory-RMS Headless startup runs for 12 seconds with empty stderr. Live login/gameplay is unverified.
- Maintained build: `tools/luna/build-max-final.ps1 -DependencyDir <ASM/MicroEmulator folder> -BuildTag <fresh-tag> [-Publish]`. Both source and donor hashes are pinned. Build evidence and publication receipt: `build/max-final-release-20261017-b/verified.json`; patch catalog `max.properties`, packet/archive logs, regression logs and `emulator/`. A separate rebuild produces the identical candidate hash. The initial script stage stopped on a PowerShell treatment of javac warnings; it did not publish. `Run-Checked` now checks native exit codes while preserving compiler output.

## HSO CHIBI Up X3 (2026-10-16)

- Published `../jar/chibi_up_x3.jar`, 3,030,198 bytes, SHA-256 `6153DB8885B3B74EB40F7D611FD4FD32933A5018224D4D2C35ADEE2920C47A40`. Source is the current no-cache `../jar/chibi_up.jar`, SHA-256 `34BCFBF74DB570954DFA563E931D98F84DB9796EE018FB75570EC2EC2472E500`; launcher template remains `../jar/backup/autotrade_v27_revive05_horse_x3.jar`, SHA-256 `5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`. This is a new release filename; no prior X3 file was replaced. Existing releases, source, template and golden retain their hashes.
- Suite `HSO CHIBI X3`, vendor `HSO_CHIBI_X3`, one native launcher `MrQuyet.MrQuyet`; menu `HSO CHIBI 1/2/3` opens `TemMidlet`, `b.TemMidlet`, `c.TemMidlet`. The 259 source classes become 258 local copies per slot plus one byte-identical root shared EXP backend, 19 unchanged wrapper classes and six lifecycle/resource helpers: 800 classes total. Source resources, wrapper/icon bytes and virtual INI source manifest are preserved. The only allowed cross-slot game reference is the shared EXP backend.
- Native accounts, monster selection, Farm settings and Auto Attack settings use `aHSO_CHIBI_`, `bHSO_CHIBI_`, `cHSO_CHIBI_`; no single-client data is imported. The shared EXP backend uses `aHSO_CHIBI_hbs_exp_levels_v2`, outside slot static reset. Endpoint, donor login/authentication, default 650ms delay and all fifteen disabled monster-cache APIs retain the source behavior in all three clients.
- `ChibiX3HostTest` passes 9,077 checks under `-Xverify:all`: exact archive topology, source-to-root transform, complete b/c canonical parity, all 800 classes linked, class versions at most 51, endpoint/auth presence, account/selection/Auto Attack storage, scoped list/delete, old single records untouched, shared EXP persistence and static restart. A fixture correction passes a cloned native account buffer because the unchanged native encoder mutates its argument in place; no game bytes changed between staged attempts.
- Source regressions pass again: six donor login packet cases, 16 native no-cache/death/animation/world-removal checks and native local trade. Semantic/controller/route/Auto Combat/Login Material suites, Auto Attack 170 and EXP 38,735 checks pass. MicroEmulator opens all three virtual clients, returns via actual held-`*` callbacks and switches 1→2→3→1 while retaining instances, canvases and three live game loops; stderr is empty. Menu and all three Chibi login screens were visually inspected on the identical candidate hash. No real account login or three-account server gameplay was tested.
- Final evidence and publication record: `build/chibi-up-x3-release-20261016-b/verified.json`, `x3-pack-report.txt`, `x3-host.txt`, individual regression logs and `launcher.{out,err}.log`/`screenshots/`. Earlier `toolcheck` and initial release stages were not published. Rebuild/publish: `tools/luna/build-chibi-up-x3.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag> -Publish`. Packer option: `HungBySonX3Packer ... CHIBI`. Usage/data layout: [HSO_CHILD_X3.md](HSO_CHILD_X3.md), Chibi Up variant.

## HSO CHIBI: disable monster cache and corpse retention (2026-10-16)

- Published `../jar/chibi_up.jar`, 1,334,890 bytes, SHA-256 `34BCFBF74DB570954DFA563E931D98F84DB9796EE018FB75570EC2EC2472E500`. Previous cached CHIBI hash `8B0F9551165EA4A48BBC96C6ED82B9C6F56424113B3EDC51584B49BA28FCCE5E` is backed up to `../jar/backup/chibi_up-before-chibi-no-cache-release-20261016.jar`.
- `ChibiDisableMonsterCachePatcher` disables all fifteen public cache entry points and its static buffer initialization. No learning, load/save, restore, polling, combat-HP observation, corpse retention or QCache HUD runs. `hold`/`advanceCorpse` return false and the native death/world-removal/animation/respawn branches remain intact. Cache-backed stalled-target rechecks stop; native loaded monsters and saved selection still populate the selection menu. Existing cache records are not imported or deleted. Login, endpoint, suite identity, Auto Attack settings and EXP data retain the previous CHIBI behavior.
- Compared with the cached CHIBI, only `luna/features/MonsterCacheSupport.class` changes; the canonical inverse proves every other member unchanged. Archive topology, native classes, manifest and resources are byte-identical to that CHIBI base. The 16-check `ChibiNoMonsterCacheTest` reproduces retention in the cached base, verifies zero callbacks through all public cache APIs, and exercises actual native death/animation plus packet-8/world-vector removal with cache disabled. Timed native respawn and live gameplay have not been exercised; their native method bodies are unchanged.
- Final pipeline passes six donor login packet cases, 103 storage checks, archive/server/reference checks with 259 linked classes, local services/enhancement/NPC/native trade regressions, semantic/controller/route/Auto Combat/Login Material/Auto Attack/EXP host suites, and 12-second isolated headless startup with empty stderr. Other endpoint artifacts and golden remain unchanged. Evidence/publication record: `build/chibi-no-cache-release-20261016/verified.json`; exact cache-member boundary: `no-monster-cache-boundary.txt`; runtime fixture: `native-monster-lifecycle.txt` in that directory.
- Rebuild uses the updated `tools/luna/build-chibi-up.ps1`: it first reproduces the pinned cached CHIBI base from Hung/Hso, then applies this endpoint-only cache profile before checks and publication. Shared monster-cache source and Hung single/X3 releases are unchanged.

## HSO CHIBI from current Hung By Son (2026-10-16, initial cached build)

- Artifact: `../jar/chibi_up.jar`, 1,337,117 bytes, SHA-256 `8B0F9551165EA4A48BBC96C6ED82B9C6F56424113B3EDC51584B49BA28FCCE5E`. Source is the current `../jar/Hung_By_Son.jar`, SHA-256 `BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2`. Current endpoint/authentication reference is `../jar/Hso.jar`, SHA-256 `24B6563FA347F63245AF2432EC215367FFDEFE33830C46E111CDAA91E9B4DDAD`; this differs from the older reference used for `chibi_final.jar`.
- Both native server tables expose exactly one row, `HSO CHIBI` / `103.77.173.232`, at index 0; native direct connection uses port 19129. All six table writes use the singleton factory, all fifteen selector writes force zero, port/language arrays have one element, and cached/remote list parsing plus list-only HTTP fetches are disabled.
- The shared opcode-1 serializer keeps raw username/password and sends the donor's version `9.9.9`, extension strings `0`, `0`, and `0|<signature>`. It uses the donor's exact 16-hex signature helper with username and the current folded handshake key, including its bounded 10×15ms key wait; the device tail is followed by the donor's final UTF `LOCK`. Four existing native callers share this boundary. Six full-payload cases compare independent donor/candidate helpers and hard-coded signature goldens, including Unicode credentials, distinct/null/empty keys, selector and device variations. Both original key handlers apply the same cumulative XOR fold before marking the session connected. Only synthetic credentials were used.
- Name/vendor/MIDlet label are `HSO CHIBI`; the original icon, entrypoint, MIDlet version and profile are retained. All native and MOD RMS operations use `HSO_CHIBI_`, including Auto Attack settings and the shared EXP table. EXP legacy import is disabled. The 103-check storage fixture seeds valid native/MOD/current/legacy EXP records and proves fresh reads, old-data preservation, scoped list/delete, new saves and persistence across a new classloader.
- Archive/server verification passes: 1,037 entries, 259/259 classes linked under `-Xverify:all`, 731 resources, class major at most 51, original class versions preserved, exact modification/addition allow-list, CRC/resource/manifest checks and 30 new references resolved. Native dial probes cover both locale flags and the already-connected guard. Local services, enhancement wait/NPC session and native local-trade regressions pass, along with semantic/controller/route, Auto Combat, Login Material, Auto Attack and EXP host suites. Isolated headless MicroEmulator startup is observed for 12 seconds with memory RMS and empty stderr. Live server login/gameplay and rendered UI are untested.
- Rebuild/publish: `tools/luna/build-chibi-up.ps1 -DependencyDir <ASM/MicroEmulator dependencies> -BuildTag <new-tag> -Publish`. Maintained patcher: `tools/luna/ChibiUpPatcher.java`; fixtures: `fixtures/hung-by-son/Chibi*Test.java`. Evidence: `build/chibi-up-release-20261016-v2/`, including `chibi.properties`, packet/storage/archive logs, `storage-report.properties`, emulator logs and `verified.json`. The build reproduces the same candidate hash; source/reference, all pre-existing release JARs and golden remain unchanged.

## Hung By Son single + X3: map48 cache bypass, inventory combat and resume100ms (2026-10-14)

- Published single `build/hung-map48-inventory-resume100-20261014-d/`, SHA-256 `BCF304BB6245CDBB6F48D5888404F8186E4E0895E2DCDAF608C68D53FEADA6E2`, replacing A235. Backup: `../jar/backup/Hung_By_Son-before-hung-map48-inventory-resume100-20261014-d.jar`. Published X3 `build/hung-map48-inventory-resume100-x3-20261014-c/`, SHA-256 `4997CF94549554D4A2E70EF50692659A000D0C0D6413A13360D3625077F12796`, from that exact published single, replacing F7EB. Backup: `../jar/backup/Hung_By_Son_x3-before-hung-map48-inventory-resume100-x3-20261014-c.jar`. Both publish records prove atomic replace/read-back, unchanged unrelated endpoints and golden. The first single replacement was refused by Windows while the user's Zang emulator held the JAR; the user closed it and publication resumed with the same verified preimage/backup.
- Maintained monster cache skips map48 before loading/learning/restoring/recovery and before every RMS write, including forced flush/retry. Native entities remain untouched. Legacy map48 contexts are validated and filtered on load elsewhere; the next permitted save excludes them and preserves other-map data. Same-process49→48→49 with dirty records/failed save and retained49/50 data passes. Schema/namespace unchanged; only the semantic cache and its existing nested classes are recompiled, not the native packet parsers.
- New Auto's first successful cast after manual movement uses elapsed>=100ms; later casts use the configured shared delay. The cast initializer captures the one-cast resume exception, so the request minimum is100 instead of650/1000, while the native strict max(skill interval,minimum) gate remains. Native request100/101 and1500/1501 boundaries pass. Failed initialization retains the resume exception. A ready map/zone/player transition during movement invalidates stale work and retains ordinary lifecycle cadence. Farm's movement policy/manual/remote request floors remain unchanged.
- Native inventory already updates its parent world and the global module loop. The bridge now accepts the exact tab singleton with world parent, valid selected index, exact native bag panel/type0, without adding a second tick. Sell16/info15, other panels, modal/login/dead/wait and invalid parent/screen remain guarded. Direction keys in inventory navigate the UI without yielding combat; native selection/cast/request sends one real monster packet and leaves inventory open. Serializer/opcodes unchanged.
- Exact single full stage passes Auto binary1803, semantic170, cache235, native input24, timing2573 at five speeds, EXP38735/native132/two-process RMS, stale99+16, selection46+native, crowded45044+27, recovery36, remote477, tempo94, Noclip24/349, offline94, enhancement13/NPC17, trade, AutoLogin242 and five semantic suites. Archive1036 entries/258 classes,636 introduced member references,778 unchanged non-class entries,13 reported changed classes (8 native/bridge plus5 semantic cache classes),9 additions; full native hook inverse, versions/CLDC and startup12s empty stderr pass.
- Exact X3 full stage passes797 links,1854 full parity/namespace/cache/reset checks, Auto299 (UI72/write191/read29/parent7), EXP364, crowded74, semantic170 plus five suites, held-* /1→2→3→1 same instances/three live loops and empty stderr. First resume and ready map/zone changes during walking, inventory type/panel/index guards, UI keys, File RMS and all three cache namespaces execute.19 wrappers/icon/source resource files remain byte-identical; one root shared EXP backend remains outside slot reset. No account or live server gameplay tested.
- Source/donor remain pinned188C/A8 as in the previous entry; rebuild single uses `../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar`. X3 packer now pins BCF304 and the unchanged258-name class-set digest. Catalogs `symbols/hung-auto-attack-20261014.properties` and `symbols/hung-auto-attack-x3-20261014.properties` are copied from the verified stages. Optional `-StageFromVerifiedCandidate` supports isolated X3 staging when the single endpoint is locked; publisher still requires the exact published single and hash-bound publication transition. Final X3 uses the current published single. [Test evidence and backups](HUNG_BY_SON_MAP48_INVENTORY_TESTS.md); [behavior](HUNG_BY_SON_AUTO_ATTACK.md).

## Hung By Son single + X3: shared Farm delay and manual movement priority (2026-10-14)

- Published single `build/hung-auto-shared-delay-move-20261014-a/` with SHA-256 `A23574A528F8DDF668D07021BCEAB722AD4057ECBEDCB28FCE4A34DDBA17BC34`, replacing 36BAC. Source remains pinned188C from `../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar`, donor unchanged A8. Backup of replacement preimage36BAC: `../jar/backup/Hung_By_Son-before-hung-auto-shared-delay-move-20261014-a.jar`. Published X3 `build/hung-auto-attack-x3-20261014-c/`, SHA-256 `F7EBBB2D80CC7DE5D338776BF561CEF5D8E7CDC117729341799D5273DF60EE2E`, from exactly the verified/published A235 single. X3 preimage9198 backup: `../jar/backup/Hung_By_Son_x3-before-hung-auto-attack-x3-20261014-c.jar`. Both `publish.json` records prove atomic replacement/read-back and unchanged unrelated endpoints/golden.
- User updates the previous independent-delay requirement: configured delay now applies to both Auto Farm and Auto đánh. Default stays650; entering1000 makes both use1000. Native Farm auto gate reads the shared real-time setting; per-cast Farm provenance is captured only from the exact automatic choose call on its initiating thread/local actor. Farm-origin and new Auto requests use strict `>max(delay,nativeSkillInterval)`; manual/remote casts stay650 even while Farm is ON. Selection/skills/paths/settings other than the intentionally shared delay remain separate. RMS format unchanged; editing delay does not enable/switch owners.
- Manual movement preempts new Auto casts. Exact held-direction/native player-walk hooks run before cG2 movement guards; accepted blank-ground and portal-navigation hooks run after native validation and before path creation. They invalidate pending tagged work, release attack state, retain ON intent and pause through held keys, queued path and walking. Stopping resets the wait to the configured delay. Menu direction keys and entity/NPC taps are excluded; manual/remote casts and Farm movement policy retain existing behavior.
- Single full pipeline passes Auto semantic111, native Auto/Farm/movement1663, actual key/touch input24, timing2565, 258 linked classes/586 introduced references, EXP38,735/native132, cache217/stale99+16/selection46+native/crowded45,044+27/recovery36/remote477, tempo94, Noclip24/349, FullOffline94, enhancement13/NPC17, trade, AutoLogin242 and all five semantic suites. Native Farm gate/init/serializer edges1/650/1000 and request provenance, stop-delay boundaries, stale-callback cancellation and actual RMS mode-switch persistence are covered. Startup12s has empty stderr. Single archive remains1036 entries/258classes/778 unchanged non-class entries; 8 declared class payload changes and9 additions, full native inverse, unchanged manifest/unreported classes/resources, no forbidden added CLDC refs.
- X3 packer now accepts the exact258-class verified source/class-set digest; copies257 per-slot classes plus one byte-identical root `ExpEstimateSharedRmsStore`, 19 unchanged launcher wrappers and six slot lifecycle/helper classes:797 total. Native account/Farm namespaces remainainv_/binv_/cinv_; new Auto settings useahbs_auto_attack_v1/bhbs_auto_attack_v1/chbs_auto_attack_v1. Only root EXP cross-slot references are allowed; backend is excluded from slot reset. Virtual Alert/AlertType and Display overloads are mapped; helper uses StringBuffer. Source resource files/wrapper icon remain exact; manifest/INI intentionally identify the virtual launcher. ZIP directory records are omitted by the packer; this does not alter file resource bytes.
- Final X3 stage passes797 links, host1839 complete slot parity/RMS/defaults/shared EXP, crowded74, Auto/UI/lifecycle/File RMS190 (UI72/write82/read29 plus parent checks), EXP364 including shared two-process File RMS and CLDC-shaped checks, semantic111 plus the five suites, and actual launcher held-* /1→2→3→1 retaining all instances/live loops with empty stderr. The UI worker opens three real virtual clients, executes packed TextBox Save/Cancel/invalid Alert and returns to each correct Canvas; movement worker uses real native held-key/player-walk hooks in all slots. Full native combat body evidence is bound to the single1663 suite plus complete slot bytecode parity. Launcher/client and native HUD/notice screenshots were visually inspected. No live server or account gameplay was tested.
- Current catalog: `symbols/hung-auto-attack-20261014.properties` (A235). X3 sidecar: `symbols/hung-auto-attack-x3-20261014.properties`. Pipeline/publisher: `build-hung-auto-attack.ps1`, `publish-hung-auto-attack.ps1`, `build-hung-auto-attack-x3.ps1`, `publish-hung-auto-attack-x3.ps1` under tools/luna. X3 requires a hash-bound verified single proof with shared-delay/movement/input gates. Partial X3 stages a/b were not published; staging fixes covered assembly loading and excluding ZIP directory records from file-byte comparison. [Behavior and usage](HUNG_BY_SON_AUTO_ATTACK.md); [latest test evidence](HUNG_BY_SON_SHARED_DELAY_X3_TESTS.md).

## Hung By Son single: independent Auto đánh (2026-10-14)

- Published the fully verified stage `build/hung-auto-attack-20261014-final-c/` to `../jar/Hung_By_Son.jar`, SHA-256 `36BAC516461C161CC5597608988C6DD1E6AE9A1E518AD06588EEDEC3207645F0`. Exact source/preimage: `188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD`; donor remains `bachho_final.jar` / `A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E`. Verified backup: `../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar`. `publish.json` records atomic replacement, read-back hash and unchanged other endpoints/golden. X3 was not rebuilt.
- Added `Menu → Auto đánh >`: master toggle, monsters-only mode 7, monsters plus eligible PK mode 6, and an integer delay editor accepting 1–1000ms, default 650. Choosing a mode enables the master. Mode/delay persist in a separate direct MIDP store; a fresh client starts OFF. The HUD adds one native-font status row only while enabled and respects the existing native info toggle. Invalid input shows an alert and returns to the same editor; Cancel retains the value.
- Controller intent, target, epoch, skill slot and real-time cadence are independent of Farm's filters, skills and location/config records. Two-way automatic handoff cancels pending native casts before ownership changes; controller-monitor locks cover final validation through actual enqueue and Farm/local-auto cleanup. Native selection/skill/mana/cooldown/animation/serializers remain in use. New tagged requests use strict `elapsed >max(delay,nativeSkillInterval)`; untagged native requests retain 650ms, without donor +300ms. Same-process map/zone/player/death/dialog/disconnect transitions invalidate work and pause intent; transient native world-access failures also pause, while explicit OFF/Farm enable stops it.
- Preserved the native HP/MP helper while new Auto owns combat; its existing settings and throttle stay. Actual cleanup tests discovered that the pinned source deletes Farm's `sys_auto_cfg`/`sys_farm_loc` records. One verified call replacement retains exactly those two logical names and delegates every other deletion to the existing EXP guard. Native account/EXP retention and normal cache deletion remain; two File RMS JVMs verify new settings plus byte-exact native Farm/account/EXP cleanup preservation.
- Final checks: Auto semantic102, native Auto1324, actual keyboard/touch input24, including 30 combinations of x1/x2/x3/x0.8/x0.9 × delay1/650/1000 × native interval0/1500, native MP/cooldown refusals, repeated casts, raw native fanout, opcode9 monster/opcode6 PK payloads and a blocked-enqueue/Farm-switch race. Cache217, stale99/native16, selection46/native, crowded45,044/native27, recovery36, remote-animation477, unchanged legacy timing2545, tempo94, Noclip24/349, FullOffline94, enhancement13/NPC17, trade, AutoLogin242, EXP38,735/native131, File RMS/CLDC and all five semantic/controller/route/combat/material suites pass. Startup runs 12 seconds with isolated memory RMS and empty stderr. HUD status240/450 and native EXP/notice images were visually inspected. No live account or server gameplay was tested; the test report distinguishes isolated native paths and stub boundaries, including the pre-existing stale fixture's missing-animation trace.
- Archive: 1,036 entries /258 classes /778 unchanged non-class entries, 7 declared native/bridge class changes and 9 additions; old native versions, manifest/resources and all unreported classes remain unchanged. Full structural hook inverse, `-Xverify:all` linkage and 503 new member references pass; added classes contain no forbidden CLDC APIs. Failed pre-verification stages `final-a`/`final-b` exposed only staging-fixture path/classpath errors; corrected script produced the same candidate hash, and only verified `final-c` was published.
- Rebuild using `tools/luna/build-hung-auto-attack.ps1` with the backup above as `-InputJar`, pinned donor, dependencies and a fresh build tag. Publication helper: `tools/luna/publish-hung-auto-attack.ps1`. Catalog: `symbols/hung-auto-attack-20261014.properties`. [Usage and behavior](HUNG_BY_SON_AUTO_ATTACK.md); [test report and evidence](HUNG_BY_SON_AUTO_ATTACK_TESTS.md).

## Hung By Son single + X3: original native notice positions restored (2026-10-13)

- User requests original notification positions now that the HUD width is back to 96px. Published verified stage `build/hung-exp-native-notices-20261013-a/`: single SHA-256 `188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD`, X3 `9198FDC1DEF6AC0FD7779DCCD70D56921D56B9906E7EA294BFB7D15BD34B08BF`. Sources remain F806/9D; replacement preimages are the shared-cache releases D94DE96E/ABC5CEFD recorded below. Backups: `../jar/backup/Hung_By_Son-before-hung-exp-native-notices-20261013-a.jar`, `../jar/backup/Hung_By_Son_x3-before-hung-exp-native-notices-20261013-a.jar`. `publish.json` records verified atomic replacement and unchanged other endpoints/golden.
- Removed the baseY hook entirely from each native three-row notice renderer. Patcher compares its original/final canonical instructions, branches and exception boundaries and records `.notice.native.unchanged=true`, `.notice.hooks=0`, `notice.position=native`. Original backgrounds/clips/text positions, widths, order, scrolling and lifetimes are preserved. The compatibility layout helper now passes nativeY through unchanged; it is not called by the native renderer. No y76 clamp remains.
- Kept the level-only v2 cache, one shared EXP backend across X3 slots, native login RMS preservation/migration, CLDC-compatible direct MIDP access, dirty-level merge/retry logic, compact 96×56 frame, K/M/no-Approx formatting, EXP row and mail layout. Account/monster data and 650ms/P/V/crowded fixes stay.
- Full pipeline passes: EXP semantic38,735; native EXP/notice125 single /249 linked classes and343 X3 /770; actual cleanup/migration, same-level sharing, independent live totals, two-JVM File RMS and CLDC-shaped old-error/new-backend tests. Both native viewports450/240 were rendered and checked at their original baseY; final screenshots were visually inspected. All previous cache/stale/selection/crowded/native/remote-animation321/timing2,502 and five semantic suites, X3 crowded74, startup and launcher pass with empty startup/launcher stderr. Resources/manifests, unreported classes and X3 wrappers are byte-identical. Added EXP class counts remain7/19 with no forbidden CLDC references. No live server test was performed. Catalogs: `symbols/hung-exp-native-notices-single-20261013.properties`, `symbols/hung-exp-native-notices-x3-20261013.properties`; behavior: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md).

## Hung By Son single + X3: shared level cache and native RMS persistence fixed (2026-10-13)

- Published passing stage `build/hung-exp-level-persist-20261013-c/`: single SHA-256 `D94DE96E2BA6A660B55CB11CF0D0CBAC7EA4F5B81FB6BD4032EC3D9E9F0129EA`, X3 SHA-256 `ABC5CEFD7EEFEB639EF1EFEA7ADBCD1CC7633BD4FB82FDCBFB97E1EDF2BF109D`. Sources remain the pinned F806 crowded single and 9D pre-EXP crowded X3. Replacement preimages are `32102A36BBE08C18F40944529FC5132A6405678FB483DFF9C9263E2088146FFF` / `484A8FECABE48D68907FE2C747F50657503C5F03CED3E9E13F8955AD1D1B16CE`; verified backups are `../jar/backup/Hung_By_Son-before-hung-exp-level-persist-20261013-c.jar` and `../jar/backup/Hung_By_Son_x3-before-hung-exp-level-persist-20261013-c.jar`. `publish.json` proves replacement and unchanged other endpoints/golden. An orphaned isolated CLDC probe held the single JAR open; it was stopped after its test-class/process identity was verified, then publication resumed. The user's separate MicroEmulator process was left running.
- User confirms the required EXP table is the same for all characters/servers. Persisted bounds now key only by level, with up to 255 entries in v2. Full endpoint/character/level identity still separates live samples, so a character change cannot append another character's earned EXP. A single root `ExpEstimateSharedRmsStore` is shared by all three X3 adapters, while the old account/cache/selection RMS identities remain separate. Writes contain only changed levels and are merged with the stored table under one shared lock to avoid stale-slot overwrite; unknown-level reads and failed writes retry at most every five seconds. Missing/corrupt reads retain valid memory bounds; unreadable rows do not erase other valid levels.
- Found two additional persistence failures missed by earlier JVM-only checks: the legacy namespace helper's Java 7 string concatenation emits `java/lang/StringBuilder`, absent on CLDC 1.1, and native login cleanup deletes `exp_approx_v1` because its keep-list only contains five configuration/account names. The new backend opens `hbs_exp_levels_v2` directly through MIDP, outside the cleanup namespace. A verified hook imports surviving `inv_exp_approx_v1`, `ainv_exp_approx_v1`, `binv_exp_approx_v1`, and `cinv_exp_approx_v1` before each native cleanup entry. Only old EXP deletion is skipped; all other native deletion calls and exception behavior are forwarded unchanged. V1 files remain intact even if migration cannot be saved; already-deleted old data cannot be reconstructed and that level must be sampled once again.
- Passed semantic EXP 38,735 checks; native EXP/HUD/notice 124 / 249 linked classes for single, 340 / 770 for X3. The maintained native fixture executes each actual cleanup method with legacy/probe records, verifies migration/retention and normal probe deletion, shares an estimate across different character/server identities, keeps live totals independent, and saves two levels in one JVM then reloads them in a fresh File RMS JVM (X3 writer root, reader b). A child-loader CLDC-shaped regression reproduces the legacy missing-StringBuilder failure and verifies the new backend can save/read a level in that condition. Constant-pool validation rejects unsupported APIs in all 7/19 added EXP classes. Final compact-HUD and 240px notice screenshots were visually inspected; the 96×56 frame, regular white EXP font, no-Approx formatting and notice y76 stay.
- Cache217, stale99/native16, selection46/native, crowded45,044/native27, recovery36, remote-animation321, unchanged real650 timing2,502, all five semantic/controller/route/combat/material suites, X3 crowded74, 12-second single startup and held-`*`/1→2→3→1 launcher pass. Startup/launcher stderr is empty. Resources/manifests, unreported class payloads and X3 wrappers remain byte-identical; only four native payloads plus seven additions change single, twelve plus nineteen change X3. No live account/server gameplay was tested. Catalogs: `symbols/hung-exp-level-persist-single-20261013.properties`, `symbols/hung-exp-level-persist-x3-20261013.properties`; behavior/implementation: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md).
- Failed/partial build logs remain in `-a` (Windows PowerShell's unsupported Contains overload, fixed to IndexOf) and `-b` (interrupted to include the final CLDC worker); only verified `-c` was published. A temporary File RMS proof remains outside the workspace at `C:/Users/BUI THE THUAT/.microemulator/hbs-exp-rms-proof/suite-Hung_By_Son Investigate/`; automatic approval review rejected its cleanup as blocked by policy. No further deletion was attempted; final maintained test homes are under the stage's `build/` paths.

## Hung By Son single + X3: EXP relog restore and compact HUD published (2026-10-13)

- Published both verified candidates from `build/hung-exp-relogin-compact-20261013-c/`. Single SHA-256 `32102A36BBE08C18F40944529FC5132A6405678FB483DFF9C9263E2088146FFF`; X3 SHA-256 `484A8FECABE48D68907FE2C747F50657503C5F03CED3E9E13F8955AD1D1B16CE`. Immutable sources remain crowded single F806 and original crowded X3 9D as recorded below. `verified.json` separates those sources from release preimages `4444433EF01CC81ED18C0D56B43AF3E81B813A2E1A2B5581D507950E13EC34F3` / `A071A5C3064EC56EA0A88E3BC84F1B1CCFD01E959D267573B25E97611FA6108E`.
- Reproduced the restore defect in the native login parser's instruction order: it writes level and progress before the final character ID. The earlier progress observer therefore loaded cache under a default/stale ID. The new observer runs directly after the final ID write, with exact order/count checks and inverse canonical verification. HUD also reconciles a missing/different session once using the completed native identity; stable frames do not read/write RMS or reset samples. The same endpoint/character/level key, RMS v1 format and slot prefixes remain, so existing learned estimates and other saved data are retained.
- Removed the display suffix: `(37.4M/100M)`. K/M rounding and `Caculating...` stay. The native frame's original 96px width and four center tiles are preserved; its extra EXP row keeps the height at 56px. Only the EXP row uses the existing regular white Tahoma 7 font. Native measurements are 60px for `(37.4M/100M)`, 78px for `(459.8M/1229.4M)` and 82px for `(1229.4M/1229.4M)`, fitting the 88px interior. All digits, K/M, dot, slash and parentheses draw visible glyphs. HP/MP/Lv fonts and original control geometry remain; mail y59 and notice minimum y76 stay.
- Passed: EXP semantic 38,711; native EXP/notice 107 checks / 248 linked classes for single and 308 / 769 for X3. Native fixture checks completed-identity hook order, the ID-default-to-final-ID RMS reload before a new gain, session isolation, same-level samples, cached-line guard, compact font widths/glyph pixels and 450/240 notice viewports. Screenshots in `single-screenshots/` and `x3-screenshots/` were visually inspected. Cache217, stale99/native16, selection46/native, crowded45,044/native27, recovery36, remote animation321, preserved 650ms timing2,502, all five semantic/controller/route/combat/material suites, X3 crowded74, single12s startup and held-`*`/1→2→3→1 launcher pass; startup/launcher stderr is empty. No live account login or gameplay was tested.
- Archive resources/manifest, unreported classes, X3 wrapper classes and all other endpoint releases remain byte-identical; golden is unchanged. Single changes four native payloads and adds six EXP classes; X3 changes twelve and adds eighteen. `publish.json` records atomic replacements and verified backups `../jar/backup/Hung_By_Son-before-hung-exp-relogin-compact-20261013-c.jar` and `../jar/backup/Hung_By_Son_x3-before-hung-exp-relogin-compact-20261013-c.jar`. Earlier failed staging `-a`/`-b` logs remain for diagnosis; only the passing `-c` stage was published. Catalogs: `symbols/hung-exp-relogin-compact-single-20261013.properties` and `symbols/hung-exp-relogin-compact-x3-20261013.properties`. Behavior/build details: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md).

## Hung By Son single + X3: crowded baseline and notice clearance published (2026-10-13)

- Corrected the single source per user clarification: `build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar`, SHA-256 `F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892`. Added the same persisted EXP feature and notice fix, publishing it as `../jar/Hung_By_Son.jar`, SHA-256 `4444433EF01CC81ED18C0D56B43AF3E81B813A2E1A2B5581D507950E13EC34F3`. The source crowded artifact remains byte-identical; its cache/watch/HP bridge repair is retained. X3 was patched directly from the verified pre-EXP crowded X3 backup `9D8966C322871B8F16180B69F0C7A6EFD905CA7BAAE0F5C9B3F4B07B35AA3A1E`; new `../jar/Hung_By_Son_x3.jar` SHA-256 `A071A5C3064EC56EA0A88E3BC84F1B1CCFD01E959D267573B25E97611FA6108E`.
- Verified stage: `build/hung-exp-crowded-notice-20261013-b/`. `verified.json` distinguishes the actual source hashes F806/9D from replacement preimages 7A705/76B072. `publish.json` records both atomic replacements, backups and unchanged unrelated endpoints/golden. Backups of the immediately preceding EXP releases: `../jar/backup/Hung_By_Son-before-hung-exp-crowded-notice-20261013-b.jar` and `../jar/backup/Hung_By_Son_x3-before-hung-exp-crowded-notice-20261013-b.jar`. Existing manifest/suite, RMS identities and EXP record version are unchanged; accounts, monster data and learned EXP are not deleted or migrated.
- Fixed the reported overlap at the native three-row notice renderer. A single hook clamps the initial drawing baseY to at least 76 only while the player panel is visible. All three backgrounds/clips/text rows move together below the 56px panel and mail/count y59–72; hidden/no-player cases retain the native baseY. Strings, order, width, marquee offsets, update/lifetime code and toast bounce are unchanged. `src/luna/ui/ExpEstimateNoticeLayout.java` owns the named layout rule; version-specific visibility/notice access stays in the generated bridge. The existing full 176×56 rectangle and K/M `Approx.`/`Caculating...` behavior remain.
- Native EXP/notice fixture passes 91 checks / 248 classes for single and 260 / 769 for X3. It verifies local-vs-remote packet30, persistence/reload/slot isolation, unchanged same-level samples, notice hook scope, visible clamp and hidden pass-through, and pixel identity above row76. Real native layout uses screen widths 450/240 and computes its unchanged notice widths 237/97; both widths and all namespaces were rendered, with readable marquee/toast text below the panel. Final evidence: `single-exp-notice-native-layout.txt`, `x3-exp-notice-native-layout.txt`, `single-notice-native-layout/` and `x3-notice-native-layout/`. Direct native rendering and packets are exercised on the login screen; no live account or gameplay was tested.
- Crowded source behavior remains: new candidate host 45,044 and native HP/world bridge 27 assertions pass; the pinned source/control remote-animation fixture passes 321, and archive byte checks tie its support/bridge repair to the candidate. Cache217, stale99/native16, selection46/native, HUD, recovery36, preserved real650 timing2,502, all five semantic/controller/route/combat/material suites, X3 crowded74, single 12s startup and X3 held-`*`/1→2→3→1 all pass. Startup/launcher stderr is empty. No gameplay or live EXP accuracy claim is made.
- Single archive is 1,026 entries / 248 classes (four native payloads changed, six classes added) from the crowded 1,020 / 242 source. X3 is 1,502 / 769 (twelve changed, eighteen added) from 1,484 / 751. All non-class/resource/manifest/directory bytes, unreported class payloads and wrapper bytes are identical to their actual inputs. Native inverse canonical checks retain branch and exception boundaries; entity/request timing bytes remain identical. Generated catalogs: `symbols/hung-exp-crowded-notice-single-20261013.properties` and `symbols/hung-exp-crowded-notice-x3-20261013.properties`.
- Rebuild: `tools/luna/build-hung-exp-estimate.ps1 -SingleInputJar build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar -ExpectedSingleInputHash F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892 -ReleaseSingleBeforeHash <current-single-sha> -ReleaseX3BeforeHash <current-x3-sha> -DependencyDir <dependencies> -BuildTag <fresh-tag>`. Publication uses `tools/luna/publish-hung-exp-estimate.ps1`, which now checks replacement hashes separately from source hashes. Behavior: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md).

## Hung By Son single + X3: persisted EXP estimate published (2026-10-13)

- Published `../jar/Hung_By_Son.jar`, SHA-256 `7A705CD568E3BB7BB1466EDB77DD82682BF26366E5FF811983386932DC94C382`, and `../jar/Hung_By_Son_x3.jar`, SHA-256 `76B07266688634DDFBAF9E69DFD0F63DC5691688A01DA563528ED9DA2C4517C9`. Verified stage: `build/hung-exp-estimate-20261013-e/`; `verified.json` records staging checks and `publish.json` records both atomic replacements, exact backups, unchanged other endpoints and unchanged golden.
- Inputs were the then-current single `8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953` and X3 `9D8966C322871B8F16180B69F0C7A6EFD905CA7BAAE0F5C9B3F4B07B35AA3A1E`. Backups: `../jar/backup/Hung_By_Son-before-hung-exp-estimate-20261013-e.jar` and `../jar/backup/Hung_By_Son_x3-before-hung-exp-estimate-20261013-e.jar`. X3 was patched independently in root/b/c so its crowded-monster repair is retained; it was not rebuilt from the different single baseline. Existing suite/vendor/manifest, launcher, account/cache/selection RMS and resource bytes remain unchanged.
- Packet30 gains are observed once after decoding and before the popup visibility gate, only for the exact local object. Same-level notifications retain samples; login/new-level/connection boundaries rebase. A bounded interval estimator learns after at least 1 percentage point of clean progress, stores bounds per endpoint/character/level and improves them across independent clean sessions. Unique `exp_approx_v1` RMS uses `inv_` for single and `ainv_`/`binv_`/`cinv_` for X3. Unknown text is exactly `Caculating...`; estimates use `(37.4M/100M Approx.)`, nearest one decimal with zero decimal omitted, K/M only and no B.
- HUD is a uniform 176×56 rectangle using existing sprite pixels; the original level, HP/MP and progress presentation remains, EXP follows at native content y+38, and both mail/count layouts move y45→59. Existing right-side controls are drawn after the widened panel. Screenshots cover unknown, 100M and 1229.4M totals in every namespace and were visually checked. Runtime verification uses the actual native packet handler/registry and MicroEmulator memory RMS on the login screen; it does not log into a live account.
- Passed: `ExpEstimateHostTest` 38,703 checks, `HungBySonExpBinaryTest` single 34 / X3 89 checks with 247 / 766 linked classes, own/remote filtering, same-level/new-level/disconnect, real RMS recreation/reload and physical slot prefixes with identical server/character/level and different learned bounds. Existing cache 217, stale host 99/native 16, selection host 46/native, HUD, recovery 36, native real650 timing 2,502, semantic/controller/route/AutoCombat/LoginMaterial and X3 crowded cache 74 all pass. Single startup is 12 seconds with empty stderr; X3 launcher held-`*`/1→2→3→1 and all three live loops pass with empty stderr.
- Archive boundary: single 1,020→1,025 entries, 242→247 classes (four native class payloads changed, five added); X3 1,484→1,499 entries, 751→766 classes (twelve changed, fifteen added). Directory entries, all non-class bytes and unreported class payloads are identical. Every native mutation has an inverse canonical check including branch/exception targets. New Java7 frame and inner-class metadata are remapped per namespace. Entity/request timing bytes are identical and 650ms behavior is verified.
- Rebuild from backups using `tools/luna/build-hung-exp-estimate.ps1 -SingleInputJar ../jar/backup/Hung_By_Son-before-hung-exp-estimate-20261013-e.jar -X3InputJar ../jar/backup/Hung_By_Son_x3-before-hung-exp-estimate-20261013-e.jar -DependencyDir <dependencies> -BuildTag <fresh-tag>`. Publication is separate via `tools/luna/publish-hung-exp-estimate.ps1`. Behavior and limits: [HUNG_BY_SON_EXP_APPROX.md](HUNG_BY_SON_EXP_APPROX.md). Live EXP accuracy and gameplay remain untested; `Approx.` is required because systematic differences between reported and credited XP cannot be inferred away.

## Hung By Son X3: crowded-monster repair published (2026-10-11)

- Published `../jar/Hung_By_Son_x3.jar`, SHA-256 `9D8966C322871B8F16180B69F0C7A6EFD905CA7BAAE0F5C9B3F4B07B35AA3A1E`, from the verified single candidate `build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar` (`F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892`). Candidate and evidence: `build/hung-crowded-x3-20261011-b/`. Previous X3 `E6181530A2096DFB9F3793161975C8A72A8C931BD3946DA7891FA096F2C93709` is backed up at `../jar/backup/Hung_By_Son_x3-before-crowded-fix-20261011.jar`. Atomic replacement/hash verification succeeds; `publish.json` records the backup and unchanged other endpoint releases. The published single `Hung_By_Son.jar` and golden remain unchanged.
- All three namespace copies include the complete new cache/watch/native HP bridge repair and P/V diagnostics described below. Timing remains strict 650ms, source resources remain identical, and the suite `Hung_By_Son X3` / vendor `Hung_By_Son_X3` plus existing `ainv_`/`binv_`/`cinv_` data prefixes are preserved. Existing X3 accounts, cache and selection stores retain their identities; no single-client data is imported.
- Existing `HungBySonX3Packer` uses the pinned template `5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`, retaining 19 wrapper classes and icon bytes. It validates 751 classes, three namespaces, unchanged source resources and no cross-slot references. `HungBySonX3HostTest` passes 1,741 assertions with `-Xverify:all`, complete canonical parity across root/b/c, native RMS/cache/selection isolation, initialized 650ms gates and clear/restart behavior.
- New `HungBySonCrowdedCacheX3HostTest` passes 74 assertions in one classloader: 55-record contexts, positive combat corpse release with K/W cleanup, independent P/V values across all three slots, unsolicited detail of a confirmed borrowed identity and quarantine of unconfirmed owned placeholders. The semantic fake boundary is used for this extra check; exact per-slot bytecode parity ties each copy to the already native-tested source.
- MicroEmulator launcher test opens all three clients and returns/switches 1 → 2 → 3 → 1 via the actual held-`*` callbacks, preserving their instances/canvases and three live game loops. Stderr is empty; launcher and all three login screenshots were visually inspected. No account login or crowded gameplay with three live accounts was tested. Rebuild: `tools/luna/build-hung-crowded-x3.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>`; script stages/verifies only, publication is separate. Usage: [HUNG_BY_SON_X3.md](HUNG_BY_SON_X3.md).

## Hung By Son: staged crowded-monster recovery candidate (2026-10-11)

- Staged single: `build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar`, SHA-256 `F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892`. Input is the current `../jar/Hung_By_Son.jar`, SHA-256 `8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953`. At this staging step no release was replaced; the later X3 publication is recorded above. Candidate retains the input manifest/suite and RMS identity; no saved data is migrated or deleted. Build evidence: `verified.json`, `archive-boundary.txt` and individual logs in the staged directory.
- Reproduced against the real input support: positive combat HP37 followed by the corpse hook becomes HP0; a valid unsolicited detail cannot release an unsent recovery of a confirmed borrowed identity. New recovery applies bounded positive combat HP to the exact confirmed current object, clears the corpse/retry/backoff state and matching recovery work, and releases native death/removal/quarantine counters without rebasing coordinates or canceling a live animation. It also releases the target-watch exclusion when an actual revival returns to the old HP baseline. Owned unobserved placeholders remain quarantined until a request or packet-4 observation.
- Invalid native detail writes are quarantined on the current observed/confirmed identity, including watchdog mismatches; a watchdog timeout alone does not invent death. Repeated combat HP0 preserves an in-flight normal recovery and its existing retry/dead-check schedule. Request pacing remains at least 400ms, two requests/cycle and 20/40/60s backoff. Dead-state checks skip full world lookup for healthy objects; unknown combat victims skip it entirely, and unrelated victims do not trigger another watch lookup. HUD adds `P` (type-1 combat hook entries before parsing) and `V` (combat-positive death/recovery releases), scoped to each map-entry session.
- Passed: 45,044 new host assertions including 55 IDs, 720 death/respawn cycles over six simulated hours, 43,200 half-second ticks, detail outages/late replies, malformed frames, initial/watchdog invalid detail, 65s repeated HP0, replacement/context isolation, same-baseline watchdog release and lookup bounds. New native bridge/extracted-world fixture passes 27 assertions, including exact position retention and preservation of live animation. Existing cache 217, stale host 99/native 16, selection 46/native, HUD, native recovery 36 (including twelve simulated hours), real-time 650ms callback/writer 2,502 and semantic/controller/route/combat/material pass. Isolated 12-second Headless MicroEmulator startup has empty stderr.
- Archive boundary: 1,020 entries, 242 linked classes, 731 resources, majors `{48=224,51=18}`; exactly eight class payloads change in the two support families and generated cache bridge. Resource/manifest bytes and every native game class remain identical to the input. `HungBySonCombatHpBridgePatch` has an inverse canonical scope check for the one bridge method; full `-Xverify:all` linkage and newly introduced references pass. `HungBySonRealTimeCombatHostTest --preserved-timing` adds a mode verifying the original native request/entity classes are byte-identical while executing the unchanged 650ms behavior; the existing five-argument 300ms test mode is retained.
- Rebuild: `tools/luna/build-hung-crowded-monsters.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>` with `HungBySonMonsterSupportPatcher.java` and `HungBySonCombatHpBridgePatch.java`. The fresh Investigate bridge generator also uses the shared lifecycle release emitter. No X3 rebuild or live server gameplay was performed. The user also reports remote players stop showing attacks in crowded zones while the local player keeps functioning; targeted native inspection found no verified hard 15-player cap. Lookup overhead is reduced, but that animation symptom remains unverified until live hook counts and gameplay are observed. Vietnamese findings/test guidance: [HUNG_BY_SON_CROWDED_FIX.md](HUNG_BY_SON_CROWDED_FIX.md).
- Supplementary native animation evidence: receiver victim insertion skips unresolved/removed objects but does not exclude cached corpses. The subsequent remote helper checks whether the attacker or its first target is in the death animation state and clears/returns before the receiver's native HP write. `HungBySonRemoteAnimationBarrierTest` extracts and validates that actual guard/cleanup block from each JAR; 321 assertions pass with `-Xverify:all` (`remote-animation-barrier.txt` in the candidate directory). Candidate positive HP release removes the target barrier, while dead actors and unconfirmed/replacement targets remain blocked. The control uses the original input support and bridge: positive HP alone leaves the death-animation marker and the same guard blocked. This directly verifies removal of one native animation barrier; full rendering and the user's crowded live scene remain untested. The maintained build script includes this supplemental fixture.

## HSOCHILD: một server, 300ms, x1/x2/x3/x5/x10 và X3 (2026-10-10)

- Input bản đơn `DABB9D4AD55D46DFC00D6E18441C1D15AE83A101344E0335F1649C55A9E82E90`. Thực hiện theo thứ tự: `HsoChildSingleServerPatcher` → `HsoChildTempo300Patcher` → `HungBySonX3Packer ... HSOCHILD`. Build ở `build/hsochild-single300-20261010/`; maps `symbols/hsochild-single-server-20261010.properties` và `symbols/hsochild-tempo300-20261010.properties`.
- Bản đơn SHA-256 `50BC808F4BEF0056F1A257F8C619E951A5A9F1D452F248AE4D8C7F785064734E`, X3 SHA-256 `DD974E58206EA44A720D485DDAAE376A855B715E364BD62EAC3845EFADBFFC30`. Backup trước thay: `../jar/backup/hsochild_update-before-single-server-300ms-20261010.jar` (input hash trên), `../jar/backup/hsochild_update_x3-before-single-server-300ms-20261010.jar` (SHA-256 `102FE0E5ED79311F12179608B5D8119C762B223C489D2E293D5C08C2190F08D5`).
- Hai bảng server native chỉ còn `Server lậu` / `221.121.0.242` tại row 0; sáu table writers luôn gán factory singleton, 15 selector writers ép 0. Port/language metadata giảm còn một phần tử (19129 / 0). Parser cached/remote `listServer` bị vô hiệu hóa vì nó có đường sửa bảng bằng `AASTORE`; hai HTTP fetch chỉ phục vụ danh sách server được bỏ. Đọc `local_server` / `isIndexServer` trả selector 0, cho tài khoản đã lưu ở index 8 tiếp tục được đọc nguyên vẹn. Guard socket/version và ba key UTF chuyển từ 8 sang 0.
- Auto gate và request minimum giảm 650 → 300ms thực; request vẫn `strict >max(300,nativeSkillInterval)`, giữ native skill cooldown/identity/NPC checks và pending-send-frame. Chỉ đổi initializer và hằng minimum, không rebuild legacy support. Fixture timing chạy callback/animation/writer opcode 6 thực qua transport stub đạt 2502 checks.
- Menu có đúng năm dòng x1/x2/x3/x5/x10, mã callback 101..105, speed values 1/2/3/5/10. Native game loop cap tăng 3 → 10 updates/pass, backlog reset 120 → 400ms với step 40ms; bảo đảm hai tốc độ mới có hiệu lực tại nhịp frame thông thường và giữ catch-up hữu hạn. Menu fixture 94 checks và actual-loop counts 80/160/240/400/800 đạt. Timing/menu/loop inverse-canonical checks xác nhận phạm vi ba class của bước 2–3.
- Bản đơn giữ 1020 entries / 242 classes / 731 resources, majors `{48=224,51=18}`; chỉ 13 classes đổi so với input bản đơn, manifest và toàn bộ resources giữ nguyên, CRC và `-Xverify:all` linkage đạt. Singleton fixture 35 checks (bao gồm gọi refresh multi-row), login/endpoint 185 checks. Source semantic/controller/route/AutoCombat/LoginMaterial suites và startup MicroEmulator 12 giây đạt.
- X3 giữ 1484 entries / 751 classes / 733 resources, majors `{45=1,47=21,48=672,51=57}`; 19 wrappers/icon từ template nguyên vẹn, source resources shared và không cross-slot game references. 1745 host checks gồm parity đủ ba namespace, initialized 300ms gate, RMS/cache/selection/clear/restart; singleton 34 và login/endpoint 185 checks cho mỗi namespace đều đạt. Launcher mở ba client, giữ `*`, chuyển 1→2→3→1 giữ nguyên instance/canvas/vòng lặp; stderr rỗng, ảnh cả ba màn hình hiện `Máy chủ: Server lậu` đã kiểm. Chưa đăng nhập/chơi trên server thật.

## HSOCHILD Update X3 trước khi rút server/300ms (2026-10-10)

- Source `../jar/hsochild_update.jar`, SHA-256 `DABB9D4AD55D46DFC00D6E18441C1D15AE83A101344E0335F1649C55A9E82E90`; template launcher `../jar/backup/autotrade_v27_revive05_horse_x3.jar`, SHA-256 `5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`. Hai input giữ nguyên hash. Build `build/hsochild-update-x3-20261010/hsochild_update_x3.jar`; publish `../jar/hsochild_update_x3.jar`, SHA-256 `102FE0E5ED79311F12179608B5D8119C762B223C489D2E293D5C08C2190F08D5`.
- `HungBySonX3Packer` thêm tùy chọn cuối `HSOCHILD`; default Hung cũ giữ nguyên. Suite `HSOCHILD X3`, vendor `HSOCHILD_X3`, một native MIDlet `MrQuyet.MrQuyet`. Menu `HSOCHILD 1/2/3` mở `TemMidlet`, `b.TemMidlet`, `c.TemMidlet`; virtual INI `hsochild-update-x3.ini` chứa manifest bản single. Giữ 19 wrapper classes và icon byte-identical từ template; nhân đủ 242 game classes + helper/lifecycle vào cả ba namespace.
- Mỗi client giữ mục `Server Lậu` tại index 8, endpoint list/direct `221.121.0.242:19129`, version native `4.0.2`, và guard chỉ thay UTF 4–6 bằng `TUOITHO_SECRET_KEY_2026` khi selector bằng 8. RMS slot prefixes `aHSOCHILD_`, `bHSOCHILD_`, `cHSOCHILD_` tách cả tài khoản, cache/chọn quái và static state; bản single `HSOCHILD_` và data Investigate `inv_` độc lập. Không nhập data cũ.
- Đạt: CRC/topology 1484 entries / 751 classes / 733 resources, majors `{45=1,47=21,48=672,51=57}`; source resources và wrapper bytes nguyên vẹn; không có reference game sang namespace khác. `HungBySonX3HostTest ... HSOCHILD` đạt 1745 checks, JVM `-Xverify:all` linkage đủ 751 classes, parity đủ 3 namespace, gate 650ms, tài khoản/list/delete/cache/selection, physical RMS prefix, static clear/restart và shared fonts.
- `HungBySonChildLoginBinaryTest` được mở rộng nhận namespace tùy chọn: root, `b/`, `c/` đều đạt 221 assertions (663 tổng), chạy writer 6 UTF và socket branch thực với transport/session stubs; key tăng 66 byte, server khác giữ parity, active-session guard giữ nguyên. Semantic/controller/route/AutoCombat/LoginMaterial suites đạt, source semantic kiểm riêng và không inject vào binary.
- `HsoChildX3LauncherTest ... HSOCHILD` khởi động cả ba client trong một phiên MicroEmulator memory RMS, giữ `*` qua callback thật và chuyển 1 → 2 → 3 → 1; instance/canvas giữ nguyên, đủ ba game loops còn sống, stderr rỗng. Ảnh menu và màn hình cả ba client đã kiểm. Reports/logs/screenshots ở `build/hsochild-update-x3-20261010/`. Chưa đăng nhập/chơi ba tài khoản trên server thật.
- Cấu trúc và thao tác: [HSO_CHILD_X3.md](HSO_CHILD_X3.md), mục biến thể `hsochild_update`.

## Hung By Son → HSO CHILD qua mục Server Lậu (2026-10-10)

- Theo yêu cầu bổ sung, bản hiện hành đổi `MIDlet-Name`, `MIDlet-Vendor` và nhãn `MIDlet-1` thành `HSOCHILD`; tiền tố toàn bộ RMS đổi `inv_` → `HSOCHILD_`. Cache/chọn quái lưu tại `HSOCHILD_mc_v1` / `HSOCHILD_ms_v1`. Không nhập/xóa data Investigate cũ. Patch bổ sung chỉ đổi `luna/generated/InvestigateStorage.class` và manifest; giữ byte toàn bộ logic server/key của bản trước.
- Build hiện hành `build/hung-child-storage-20261010/hsochild_update-HSOCHILD.jar`, SHA-256 `DABB9D4AD55D46DFC00D6E18441C1D15AE83A101344E0335F1649C55A9E82E90`; publish lại `../jar/hsochild_update.jar`. Backup bản trước: `../jar/backup/hsochild_update-before-HSOCHILD-data-20261010.jar`, SHA-256 `6E60A7F923F5A6509660BED882DF96814E0CCF8455174FE6BBC75543ABD5B04E`. Source `tools/luna/HungBySonChildStoragePatcher.java`; map `symbols/hung-child-storage-20261010.properties`.
- `HungBySonChildStorageBinaryTest` đạt 1280 assertions: CRC/topology/resources, chỉ 2 entries đổi so với bản trước, class versions giữ nguyên, linkage đủ 242 classes, 16 native RMS calls vẫn qua wrapper; dùng cùng memory RMS với các store native/Investigate để kiểm dữ liệu mới độc lập, list/delete/idempotent prefix, cache/selection và persistence qua classloader mới. Login/endpoint regression 221 assertions và MicroEmulator startup 12 giây đạt, stderr rỗng; report ở `build/hung-child-storage-20261010/`. Các đoạn dưới mô tả bước patch endpoint trước khi đổi identity/data.

- Input `../jar/Hung_By_Son.jar` SHA-256 `8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953`; reference `../jar/hsochild_final.jar` SHA-256 `B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671`. Cả hai input giữ nguyên byte. Artifact mới được giữ cùng bản HSO CHILD hiện có theo tên người dùng yêu cầu.
- Build `build/hung-child-login-20261010/hsochild_update.jar`; publish `../jar/hsochild_update.jar`. Patcher `tools/luna/HungBySonChildLoginPatcher.java`; exact symbols/hash/call sites ở `symbols/hung-child-login-20261010.properties`.
- Row index 8 giữ tên `Server Lậu`, IP danh sách đổi từ `103.178.234.202` sang `221.121.0.242`. Nhánh socket riêng của mục này vốn đọc host/version rỗng và port 0, nên được bind trực tiếp tới `221.121.0.242:19129`, version native `4.0.2`. Reference HSO CHILD dùng cùng IP và port mặc định 19129. Không dùng tên class/version/count của ví dụ 917-entry/2.5.8 để patch nhầm binary thực tế.
- Serializer opcode 1 chung chỉ override UTF 4–6 bằng `TUOITHO_SECRET_KEY_2026` khi selector bằng 8. Bao phủ 4 call sites thực tế: 3 lần từ native dialog và 1 lần từ hàm direct-login. Username/password/version/selector/device flags và các đường auto có sẵn vẫn đi qua serializer này; server khác giữ tham số cũ. Không thêm class hay thay tài nguyên/manifest/RMS.
- Đạt: `HungBySonParityJarVerifier` kiểm ZIP CRC, 1020 entries/242 classes/731 resources, class majors `{48=224, 51=18}`, chỉ 3 class nằm trong allow-list, manifest/resources identical, JVM `-Xverify:all` linkage đủ 242 class và 1 field reference mới. Report `build/hung-child-login-20261010/boundary.txt`.
- `HungBySonChildLoginBinaryTest` đạt 221 assertions: chạy instruction slice của serializer thật với transport stub, kiểm 6 UTF đúng thứ tự, packet header tăng 66 byte cho index 8, server khác byte-identical và giữ cả extension values khác `0`; chạy nhánh socket native với session stub, kiểm 20 tổ hợp index/locale, IP/port/version và guard connection đã mở. Fixture nằm trong `fixtures/hung-by-son/`.
- Semantic/controller/route/AutoCombat/LoginMaterial suites đạt. MicroEmulator startup 12 giây với memory RMS đạt, stderr rỗng: `build/hung-child-login-20261010/smoke/`. Chưa xác minh đăng nhập hoặc gameplay trên server thật; các kiểm bytecode/fixture/startup không thay thế xác minh đó.

## HSO CHILD X3 dùng launcher của bản mẫu (2026-10-02)

- Source game: `../jar/hsochild_final.jar`, SHA-256 `B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671`. Template người dùng chỉ định: `../jar/backup/autotrade_v27_revive05_horse_x3.jar`, SHA-256 `5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`. Chỉ lấy 19 class launcher/library và icon từ template; logic game ở cả ba client lấy toàn bộ từ source HSO CHILD hiện hành.
- Artifact build: `build/releases/hsochild-x3-final-mrquyet-20261002.jar`; publish: `../jar/hsochild_final_x3.jar`; SHA-256 `1A7E7AAA6DA7A4197E83458F70DC54D14B793FAF1588B45FEA79AF1C608AA22D`. Source, template và golden baseline giữ nguyên hash. Bản single và x3 cùng tồn tại theo yêu cầu người dùng. Report: `build/hsochild-x3-final-mrquyet-20261002/manifest.txt`.
- Một native MIDlet `HSO CHILD X3,/MrQuyet/mid.png,MrQuyet.MrQuyet`; entries `0/1/2` khai báo ba virtual client `TemMidlet`, `b.TemMidlet`, `c.TemMidlet` với nhãn `HSO CHILD 1/2/3`. Game MIDlet kế thừa `lib.MIDlet`, canvas kế thừa `lib.Canvas`; callbacks đi qua `PAINT/leyPressed/leyReleased`. Wrapper launcher và thư viện được giữ byte-identical.
- Nhân đủ 216 game classes, gồm `luna/*`, vào ba namespace. Mỗi namespace có thêm `Static` lifecycle dispatcher và helper tài nguyên; tổng 673 classes/1405 entries/732 resources, major tối đa 51. Có `cinitclone/clears` và registration cho các game class concrete, để thư viện mẫu có thể giải phóng/khởi tạo lại static state; các constant có ConstantValue giữ nguyên. 729 source resources ngoài manifest giữ byte-identical; thêm icon launcher và INI chứa manifest của source HSO CHILD.
- RMS dùng các aliases `apen/bpen/cpenRecordStore`, `aist/bist/cistRecordStores`, `aelete/belete/celeteRecordStore` của thư viện mẫu: prefix `a/b/c` nhất quán cho open/list/delete. Reflective mod config cũng tách thành `ahsosky_cfg`, `bhsosky_cfg`, `chsosky_cfg`. Không tự nhập dữ liệu từ bản single hay template Luna.
- Đạt: archive content/CRC, resource comparison, wrapper byte comparison, không tham chiếu sang game namespace khác, JVM `-Xverify:all` và field/method/constructor loading đủ 673 classes, RMS native/reflective isolation và clear/restart; 133 symbols và legacy route regression ở từng namespace. Semantic/controller/route fixtures của source hiện có được kiểm riêng, không inject source mới vào binary.
- Kiểm MicroEmulator trong **một phiên**: menu có ba dòng, khởi động cả ba client, giữ `*` về menu, chuyển 1 → 2 → 3 → 1 giữ nguyên instance/canvas và cả ba game loop còn sống. Stderr rỗng, stdout không có lỗi. Logs và ảnh menu/login ở `build/hsochild-x3-final-mrquyet-20261002/{launcher.out.log,launcher.err.log,screenshots/}`. Chưa đăng nhập/chơi ba tài khoản trên server thật.
- Công cụ/cấu trúc/cách build: [HSO_CHILD_X3.md](HSO_CHILD_X3.md).

Bản thử trước đó dùng ba native MIDlet riêng, SHA-256 `4DE7F6502B563AA5124EB91FABE07166DD4A8ADE884247E07378AB092B1653AD`, bị người dùng từ chối vì không đúng cấu trúc x3 cũ. Đã rút khỏi release root và giữ tại `../jar/backup/hsochild_final_x3-three-midlet-rejected-20261002.jar`; kết quả startup riêng từng MIDlet của thử nghiệm đó không được gán cho bản hiện hành.

## Bằng chứng đã ghi trước khi rút gọn

- Luna: HSOLUNA / 4.0.2, nền V7 menu-restored với custom reconnect chỉ khi `selectedAutoLoginChar >= 0`; manual login giữ native flow. V8–V49 là thử nghiệm đã bỏ.
- Hash Luna từng được ghi là `6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`, manifest được dẫn là `build/luna_final.manifest`. Record cũ ghi kiểm 216 classes, class/resource loading và MicroEmulator startup 8 giây đạt; không gán kết quả đó cho file hiện tại có hash khác.
- Golden từng được ghi có 192 classes: 186 major 47 và 6 support major 51.
- Last verified (record HSO CHILD): 2026-09-27; source `build/releases/hsochild-no-enhancement-animation-20260927.jar`; manifest `build/hsochild-no-enhancement-animation-20260927.manifest`; SHA-256 `B94AF1CC26C08870BAD01DDBFAA8036CDB79A4D5055B972172CDB1266592B671`. Đây là bản đã được khôi phục sau khi hai thử nghiệm repeat celestial không hoạt động trên server thật. Bản hiện hành giữ login-key patch, nhịp auto đập tối thiểu 500ms, close cleanup và tắt animation kết quả; không chứa `HsoChildCelestialRepeatSupport` hay hook mở lại NPC/menu. Có 946 entries/216 classes và 730 resources; resource comparison và JVM `-Xverify:all` class loading đủ 216 class đạt. Artifact rollback đối chiếu: `../jar/backup/hsochild_final-before-celestial-rearm-v2-20260927.jar`, cùng SHA-256.
- Patch movement trước đó vẫn được giữ: movement mask 5/8 ticks (x1.25) → 8/8 ticks (x2). Chưa thử di chuyển, basic-hit rate hoặc server acceptance trên server thật.
- HSO CHILD có cast/auto-combat gate 100ms; kiểm player-update 250ms không chứng minh nhịp Auto Tự Đánh. Chi tiết trong [LUNA_BEHAVIOR.md](LUNA_BEHAVIOR.md).
- HSO CHILD auto đập đồ dùng thời gian từ request gần nhất làm nhịp duy nhất: có response hay không, request tiếp theo không gửi sớm hơn 500ms; response cập nhật inventory nhưng không kích hoạt gửi ngay.

## Cập nhật sau phát hành

Ghi ngày `Last verified`, endpoint, filename nguồn và file được publish, SHA-256 thực tế, manifest path, các kiểm tra đã đạt/chưa chạy và backup/rollback. Thay record hiện hành, chuyển record cũ sang lịch sử; không tích luỹ nhiều dòng “latest”. Chỉ cập nhật feature/invariant trong tài liệu hành vi tương ứng, không nhân bản record vào `CLAUDE.md`.

## Hung By Son Updated: real-time 550ms and pending send frame (2026-10-06)

- Published `../jar/Hung_By_Son_updated.jar`, 1,286,161 bytes, SHA-256 `EE2DF95933C939A82D5346AA093C9BAB38B4F9347FB7048041CA649C3A1D8694`. Verified candidate and full rebuild: `build/hung-updated-real550-rebuild-20261006-c/Hung_By_Son_updated.jar`; `verified.json` and atomic `publish.json` in that directory. Preimage `2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2` is preserved in `../jar/backup/Hung_By_Son_updated-before-real550-20261006.jar`. All other release JARs, including Investigate 700ms and HungSon-update, and the golden baseline retain their hashes. Manifest, RMS and resources are unchanged.
- User requested 550ms with game speed unable to shorten skill waits. Audit shows the old auto gate and cooldown already use real time; game speed scales simulation/animation updates. The old request callback requires one exact animation frame, then `J()V` advances animation even if the interval rejected the request. That can lose a pending request; whether it caused the user's live symptom remains unproven. Updated changes auto 600 to 550, reads System time directly for the local request comparison/timestamp, and keeps strict `elapsed >max(550, nativeInterval)`. No +300ms addition. A per-cast boolean causes `J()V` to skip only its following animation advance while timing is pending, preserving the exact send frame for retry. The callback clears the flag on entry; once ready it takes the original send path and advances animation. Remote/NPC paths and longer skill intervals are preserved.
- `HungBySonRealTimeCombatHostTest` passes 2,494 checks: strict 549/550/551 boundaries, native intervals 0/100/600/32767, stale/accelerated cached-clock poisoning, unchanged pending timestamp/metadata, actual native entity callback/animation advance, native opcode-6 serialized bytes at an in-memory enqueue, no duplicate requests and eventual send at all five speeds. Actual tempo scaler counts over 1600ms are 80/160/240/64/72. The old-input comparison is structural; the new callback executes pending at +100ms and sends once at +601ms for native interval 600. The isolated fixture replaces world/render initialization, clocks, skill/target providers, common visual dispatch and network enqueue. It does not test a live connection or full player world.
- Full rebuild passes cache 217, selection 46 plus native menus/vectors, recovery 27, stale 74/15, HUD, tempo 93, Noclip 24/349, offline MOD 94, enhancement wait 13/NPC 17, trade and semantic/controller/route/combat/material regressions. Archive/CRC/resource/manifest/version and `-Xverify:all` reflection linkage pass: 1020 entries, 242 classes, 731 resources, majors {48=224,51=18}; two existing native classes change, four new references resolve. Independent inverse canonical comparison proves all unrelated class members/instructions preserved. Isolated 12-second Headless MicroEmulator startup passes. Actual server acceptance, damage and visible gameplay remain untested; 550ms is a minimum threshold, not an exact packet schedule.
- Maintained boundary: `tools/luna/HungBySonRealTimeCombatPatcher.java`; fixture: `fixtures/hung-by-son/HungBySonRealTimeCombatHostTest.java`; exact catalog: `symbols/hung-updated-real550-v2-20261006.properties`. Rebuild: `tools/luna/build-hung-updated-real550.ps1` with a fresh `-BuildTag`; its default input is the pinned preimage backup above. Earlier build attempts failed only in the new fixture (self-target produced opcode 9; NPC structural inspection selected a later type read); the release candidate bytes were identical throughout and only the fully passing build was published.

## Hung By Son: real 650ms and three-client launcher (2026-10-06)

- Published the user-requested names `../jar/Hung_By_Son.jar` (1,287,638 bytes, SHA-256 `8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953`) and `../jar/Hung_By_Son_x3.jar` (2,944,691 bytes, SHA-256 `E6181530A2096DFB9F3793161975C8A72A8C931BD3946DA7891FA096F2C93709`). Candidates/logs: `build/hung-real650-x3-20261006-b/`; `verified.json` and `publish.json` record both successful replacements/creation and unchanged other endpoints/golden. Previous single 500ms hash `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F` is backed up as `../jar/backup/Hung_By_Son-before-real650-x3-20261006.jar`.
- User clarified the initial “+100s” as 550 ->650ms and requested both filenames. The source is the latest corpse/independent-HP Investigate binary (`0F92CFF49E6BE0E7A55FF8A48B9EE3460DBA451E2C4CD0F04D232FC62DDBEA8C`), retaining those repairs, with the direct-clock/pending-frame mechanism of Updated. Auto strict >650ms; request strict >max(650ms,nativeInterval), no +300ms, unchanged separate skill cooldowns and native behavior outside waiting. The single suite identity/RMS remain Investigate; no old single-account data is imported from the replaced pre-Investigate suite. Existing Investigate and Updated JARs remain unchanged.
- `HungBySonRealTimeCombatPatcher` supports optional pinned source/before-gate/minimum arguments while preserving its historical four-argument default. Independent inverse canonical checks restrict the single change to the cast controller and entity request/animation guard. Same 1020 entries/242 classes/731 resources; all archive bytes outside those two classes, resources/manifest/class versions and other feature support are identical to the latest source. The generalized timing fixture passes 2,496 checks at five speeds, including 649/650/651 boundaries, higher skill intervals, poisoned native clock, actual queued opcode 6, held frame and no duplicate sends. Cache 217, recovery 36 (including twelve simulated hours), stale 99/16, selection 46/native menu/vectors, HUD, tempo 93, Noclip 24/349, MOD 94, enhancement 13/NPC 17, trade and semantic/controller/route/combat/material pass. Isolated single emulator startup passes.
- X3 uses the pinned `autotrade_v27_revive05_horse_x3.jar` template (`5DC9E9821904617B8A8171F45530DA3E078E6492EC6E37580E160AE6AC1732DC`): 19 wrappers and its icon are byte-identical. Suite `Hung_By_Son X3`, vendor `Hung_By_Son_X3`, one native launcher `MrQuyet.MrQuyet`, three virtual `TemMidlet/b.TemMidlet/c.TemMidlet` clients. The packaged Hung MIDlet moves to the template root; all 242 source classes, including cache/selection/watch/API/bridges, are copied per slot. Lifecycle/reset hooks and `Static` are generated per slot, private nested classes are made accessible to those hooks, resources are shared unchanged and RMS uses a/b/c aliases with the existing inv_ scope. Source and template hashes remain unchanged; 751 classes/733 resources, no cross-slot game references.
- X3 host checks: 1,740, including all-class JVM member linkage, full canonical parity of relocated b/c namespaces, initialized 650ms gates, native account save/load, RMS list/delete boundaries, real semantic learned-cache data and selection-store independence, static clear/restart preserving saved slot-1 state and unchanged slots 2/3, and shared fonts. Reports: `x3-pack-report.txt` and `x3-host-after-normalization-fix.txt`. One initial host failure was an alias spelling mistake in fixture normalization; it was fixed without changing candidate bytes. The old launcher fixture assumed a retained Thread field; Hung starts Thread(this) without storing it, so the fixture additionally checks actual live run stacks per canvas namespace. Completed verification records these fixture corrections.
- Headless launcher test opens all three clients, returns with real held-* callbacks and switches 1 ->2 ->3 ->1 with original instances/canvases and three live game loops. `x3-launcher.out.log` ends in PASS; stderr is empty; launcher and all three login screens are visually checked under `screenshots/`. No live login/combat with three accounts was tested. Rebuild/usage and limits: [HUNG_BY_SON_X3.md](HUNG_BY_SON_X3.md); pipeline `tools/luna/build-hung-real650-x3.ps1`; catalog `symbols/hung-real650-20261006.properties` and feature validation `symbols/hung-real650-features-20261006.properties`. The Investigate feature pipeline default now pins its 500ms backup because the release filename intentionally contains the 650ms variant.

## Hung By Son Investigate: retained corpses and independent combat HP (2026-10-06)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,287,554 bytes, SHA-256 `0F92CFF49E6BE0E7A55FF8A48B9EE3460DBA451E2C4CD0F04D232FC62DDBEA8C`. Candidate, full pipeline and publication evidence: `build/hung-investigate-corpse-20261006-h/{verified.json,publish.json,current-release-boundary.txt}`. Previous release `D06B80231A9197CFCEBA6719C6E9C0623F80EBECEC8D1F591D3F10545A15862F`; verified backup: `../jar/backup/Hung_By_Son_investigate-before-corpse-retention-20261006.jar`. Atomic replacement succeeds; other endpoints, including Updated, and the golden are unchanged. Final catalog: `symbols/hung-investigate-corpse-20261006.properties`.
- User reports disappearance after several hours despite the earlier retry fix, and still occasionally attacks a stale-positive-HP monster killed by somebody else. Two client mechanisms are reproduced against the actual previous JAR: positive detail leaves a pending native removal flag, which the real world removal loop then consumes and deletes the known object (`legacy-removal-repro.txt`); a loaded attacker with a missing animation module makes native processing fail before victim HP is applied (`legacy-loaded-attacker-repro.txt`, expected failed assertion on previous bytes). These are controlled fixture reproductions; the exact live packet/asset sequence has not been captured.
- Known, confirmed monster identities now remain as visible non-targetable corpses rather than disappearing. A new hook runs before native world removal; the death and frame hooks suppress synthetic revival and independent terminal cleanup. The original private death-frame table is accessed only inside its native owning method: advance to the last frame and hold there. Positive validated detail clears hidden/death/quarantine and all removal flags/counters. Unlearned entities keep native behavior; replacement identities and map boundaries are guarded. Initial unconfirmed candidates retain their earlier hidden/pending guard. RMS format, namespace and learned records are unchanged. HUD `K` counts currently retained corpses.
- Monster combat HP is parsed independently at receiver entry, before attacker and animation branches, for target type 1 only. Mark/reset preserves the native stream position and original combat flow. The observer applies only fully parsed/bounded victim HP to confirmed current identities; PK remains native. Real HP progress releases watch exclusions. Repeated three-send/2.5-second stalls now use 3/6/12/24/30-second cooldowns, capped at 30 seconds, with bounded identity history in the existing 32-slot table. Progress resets history; late unchanged replies do not extend a finished window. No HP-zero guess is made merely because attacks do not progress.
- Full build: `tools/luna/build-hung-investigate.ps1 -AutoGateMs 700 -BuildTag hung-investigate-corpse-20261006-h`. Passed: cache 217 (four simulated hours/41 IDs), recovery 36 (twelve simulated hours, hundreds of same-identity death/removal/respawn cycles, a twenty-minute reply outage, real removal-loop slice and actual frame/death methods), stale host 99/native 16 (adaptive cooldown, progress, mark/reset, loaded/unloaded attacker, malformed frames and queue boundaries), selection 46/native vectors/menu, HUD, native reference timing 82, final 700ms gate 43, tempo/Noclip, local MOD, enhancement/NPC/trade and semantic/controller/route/combat/material. Isolated 12-second emulator startup passes with empty stderr. The loaded-attacker fixture deliberately has no animation module: native still emits its expected diagnostic, while the observer updates death HP; this diagnostic is confined to that harness.
- Same 1020 entries/242 classes/731 resources and majors {48=224,51=18}; 48 new references resolve and all classes link with `-Xverify:all`. Twelve classes differ from the previous release: cache API/support family, watch/support nested exclusion, generated cache bridge, and three native classes for world/frame/HP hooks. Independent inverse canonical checks retain unrelated native instructions; combat gate/request timing, all other class/resource/manifest bytes and RMS identity are preserved. The world-loop harness substitutes its live movement/render call and uses sample frame assets/fake time; it does not run a logged-in world or verify live graphics. The combined new behavior still needs an actual long-session test.

## Hung By Son Investigate: timing matched to HungSon-update (2026-10-06)

- Published the user-requested new filename `../jar/Hung_By_Son_updated.jar`: 1,286,078 bytes, SHA-256 `2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2`. Tested candidate `build/hung-investigate-match-update-20261006/Hung_By_Son_investigate.jar` is byte-identical. The initial replacement of Investigate was blocked by an open destination (`publish.json`); the user then requested a new file instead. `../jar/Hung_By_Son_investigate.jar` remains hash `D06B80231A9197CFCEBA6719C6E9C0623F80EBECEC8D1F591D3F10545A15862F`. Verified preimage backup: `../jar/backup/Hung_By_Son_investigate-before-match-update-20261006.jar`. No manifest/RMS identity change was requested or introduced; only the filename differs from the tested candidate. Atomic new-file publication/hash verification passed; completion: `build/hung-investigate-match-update-20261006/publish-updated.json`.
- User requested the timing profile observed in `HungSon-update.jar`: auto gate strict elapsed >600ms, request interval directly from native skill metadata with no +300ms addition, native skill cooldown and animation retained. One auto initializer changes 700 -> 600; the request method and all other combat methods are unchanged. Every other archive entry is byte-identical. Cache/selection/stale-HP/long-session monster recovery and Investigate RMS/identity are preserved. `HungSon-update.jar` remains hash `DC2E35044C141BF03F63E6AACD6E9BA1264A14CCA7FCE5C6D14ACF24F76D50B0` and was not modified.
- Passed: 43 real auto-gate/scope checks for 700 ->600, and 82 original timing checks each for donor and candidate, including initialized 600ms, strict 599/600/601 boundaries, dynamic request intervals 0/100/600/32767 and comparison bytecode parity with the pinned original reference. Archive/CRC/resource/manifest/version/reflection linkage checks pass: 1020 entries/242 classes/731 resources, majors {48=224,51=18}, one modified class, no new member references. Isolated 12-second Headless MicroEmulator startup passes. Live gameplay/accepted-request timing remains untested.
- The candidate hash exactly equals the fully tested long-session release at `build/hung-investigate-long-session-20261006-d/` and its verified record; the 217 cache/27 recovery, selection/stale/HUD, tempo/Noclip, enhancement/NPC/trade and semantic/controller/route evidence for those identical bytes is retained. Only focused timing/archive/startup checks were rerun. `build-hung-investigate.ps1` default `AutoGateMs` is restored to 600 and its syntax was checked; explicit `-AutoGateMs 700` can reproduce the previous profile. Exact catalog: `symbols/hung-investigate-match-update-20261006.properties`. Other endpoint/donor/golden hashes remain unchanged.

## Hung By Son Investigate: auto gate +100ms (2026-10-06)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,286,077 bytes, SHA-256 `D06B80231A9197CFCEBA6719C6E9C0623F80EBECEC8D1F591D3F10545A15862F`. Tested candidate: `build/hung-investigate-auto700-20261006/Hung_By_Son_investigate.jar`. Previous release hash `2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2` is preserved in `../jar/backup/Hung_By_Son_investigate-before-auto700-20261006.jar`. The initial replacement was blocked because the destination was open (`publish.json`); after the user closed the game/emulator, atomic replacement and published-hash verification passed. Other endpoints/golden hashes are unchanged. Completion: `build/hung-investigate-auto700-20261006/publish-completed.json`.
- User reports that rapid auto attacks cause missed skills and requested another 100ms. Only the native auto initializer changes 600 -> 700ms; the comparison remains strict `elapsed >700ms`. The request comparison remains `elapsed >nativeInterval`, with no extra fixed interval; native animation, separate skill cooldowns, packet senders, long-session monster recovery, cache/selection/watch/HUD and all other features are preserved. This is a client pacing adjustment; whether it resolves the reported misses has not yet been tested in live gameplay.
- `HungBySonAutoGatePatcher.java` now accepts optional pinned input hash/before/after arguments while preserving its historical four-argument behavior. `HungBySonAutoGateHostTest.java` similarly accepts expected before/after values. Exact generated catalog: `symbols/hung-investigate-auto700-20261006.properties`. 43 actual auto-gate/scope checks pass, including 699/700/701ms and the prior 601ms threshold, and byte-identical request method verification. Inverse canonical comparison proves one initializer constant is the only code/member change in one class.
- Archive/CRC/resource/manifest/version/reflection checks pass: 1020 entries, 242 linked classes, 731 resources, majors {48=224,51=18}, no new member references; every other entry is byte-identical to the current long-session release. All other endpoints and golden hashes remain unchanged.
- Updated `build-hung-investigate.ps1` defaults to `-AutoGateMs 700` after restoring and testing the original native request timing; `-AutoGateMs 600` reproduces the previous original-auto profile. Full rebuild at `build/hung-investigate-auto700-rebuild-20261006/` passes and produces the exact same candidate hash. Inherited/native/final gate checks, 217 cache checks including the four-hour scene, selection 46/native vectors, recovery 27, stale 74/15, HUD, tempo/Noclip, enhancement/NPC/trade and semantic/controller/route/combat/material tests pass. Isolated 12-second Headless MicroEmulator startup passes. No live gameplay/rendering/accepted request timing was tested.

## Hung By Son Investigate: long-session monster recovery (2026-10-06)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,286,078 bytes, SHA-256 `2AF569F85FE379BD18BF770C55799CE95CAA25796F60CC13B0E36AAFA42B92E2`. Candidate and complete logs: `build/hung-investigate-long-session-20261006-d/`; `verified.json` and `publish.json` record successful atomic replacement and unchanged other endpoints/golden. Previous release SHA-256 `EBD4878C0C2955696E3395A285AD07CBBDBAB9D6D71BC35D1DAE626A3F53764B`; verified rollback copy: `../jar/backup/Hung_By_Son_investigate-before-long-session-fix-20261006.jar`.
- User reports missing distant monsters only after tens of minutes/overnight; entering the same map/zone again restores them. The previous support permanently blocked a confirmed ID after three failed recovery cycles and retained some mismatched/observed pending entries as terminal, with reset only by packet 4 or map entry. The new regression fails on the previous published JAR at same-map retry after three failures (`build/hung-long-session-repro-20261006/`). This demonstrates a client failure mechanism consistent with the symptom; the exact live packet sequence has not been captured.
- `MonsterCacheSupport` now ends every failed pending cycle, retaining only learned metadata and a bounded retry schedule of 20/40/60 seconds. Third and later failures continue at 60 seconds. Initial restore/detail failures also retry; known pending IDs that look alive still require matching detail after failure. Strict scope/object/position/level/HP checks remain. Unobserved cache-owned placeholders may be discarded; borrowed native or packet-4-adopted objects are preserved. Mismatched borrowed detail is quarantined because the native receiver may already have written its HP. Valid late HP0 resets failure backoff to the normal three-second dead-check cycle. All retry state remains ephemeral; RMS format/identity and learned cache are unchanged.
- Full pipeline `tools/luna/build-hung-investigate.ps1` default native timing profile passes. Cache host: 217 checks, including four simulated hours with 41 IDs, recurring remote deaths/removals, a twenty-minute loss/mismatch interval and complete recovery without map changes or new packet 4; late positive detail also clears quarantine immediately for an adopted object that was never confirmed before timeout. Native recovery: 27 checks using the actual patched death method and bridge, including three lost-reply cycles, pending cleanup, suppressed synthetic revival during maximum cooldown and matching positive revival of the same identity. Selection 46/native menu/vectors, stale watch 74/15, HUD, native timing 82, inherited-stage gate 43, tempo 93, Noclip 24/349, local MOD 94, enhancement wait/NPC 13/17, trade, semantic/controller/route/combat/material and isolated 12-second emulator startup pass.
- Independent comparison against the previous published JAR: same 1020 entries, 242 linked classes, 731 resources and majors {48=224,51=18}; only `MonsterCacheSupport` and its four nested classes differ (including changed debug line tables). Every native class, manifest/resource and other support is byte-identical. Report: `current-release-boundary.txt`; 19 new references resolve. Final symbol catalog: `symbols/hung-investigate-long-session-20261006.properties`; native timing evidence: `native-timing.properties` and `native-timing-test.txt` in the build directory. No server files changed. The new candidate has not been tested in a live long session; request pacing still caps at one per 400ms globally and two per ID per cycle.

## Hung By Son Investigate: original native combat timing (2026-10-05)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,286,761 bytes, SHA-256 `EBD4878C0C2955696E3395A285AD07CBBDBAB9D6D71BC35D1DAE626A3F53764B`. Candidate: `build/hung-investigate-native-timing-20261005/Hung_By_Son_investigate.jar`. Previous release/input hash: `74ADCC563B0E67DA63F4F2B242E79911A93D0A2FFE94538E7DFBCAD0C8CE2D38`; verified rollback copy: `../jar/backup/Hung_By_Son_investigate-before-native-timing-20261005.jar`. The first replacement was blocked by the file being open (`publish.json`). After the user closed the game/emulator, atomic replacement and post-publication hash verification succeeded; all other endpoints/golden hashes are unchanged. Completion record: `build/hung-investigate-native-timing-20261005/publish-completed.json`.
- The user explicitly selected restoration of the original logic, including its built-in auto gate. The pinned original pre-cast reference is `../jar/backup/Hung_By_Son-before-cast300-20260930.jar`, SHA-256 `8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4`. The original initializer/gate is restored (strict elapsed >600ms); the request comparison again uses the dynamic native skill interval directly, with no added +300ms. No newly chosen fixed request interval replaces skill metadata. Skill cooldowns, native animation, full-map target behavior, cache, selection, stale-HP watch, RMS identity and all other features are preserved.
- Source: `tools/luna/HungBySonNativeTimingPatcher.java`; fixture: `fixtures/hung-by-son/HungBySonNativeTimingHostTest.java`; exact catalog/hashes: `symbols/hung-investigate-native-timing-20261005.properties`. Only one native class changes, at the auto initializer and request comparison. Restoring the two prior overrides reproduces the complete input class canonically; the restored initializer, auto method and request comparison also match the original reference. Every other archive entry is byte-identical. 1020 entries/242 classes/731 resources, majors {48=224,51=18}, zero new member references, ZIP CRC, all-class JVM reflection linkage with `-Xverify:all`, unchanged manifest/resources/class versions pass.
- Original/native timing fixture passes 82 checks: real initialized auto gate with deterministic 599/600/601ms boundaries, extracted original/candidate request comparisons at native intervals 0/100/600/32767ms and exact boundaries, bytecode equality and absence of the +300ms addition. Final-candidate cache (111), selection (46 plus native vectors/menu), recovery (18), stale watch (74 host/15 native), HUD, tempo clicks (93), Noclip menu (24)/collision (349), local MOD (94), enhancement wait (13)/NPC (17), trade and semantic/controller/route regressions pass. Isolated 12-second Headless MicroEmulator startup passes. No live combat or server-accepted request timing was tested.
- `tools/luna/build-hung-investigate.ps1` now restores native timing by default after validating the inherited feature build; `-KeepInheritedCombatTiming` is available only for reproducing the historical inherited-timing stage. The updated full pipeline was run at `build/hung-investigate-rebuild-native-timing-20261005/` and produced the exact same candidate SHA-256. It validates both stages and runs the final native timing/cache/selection/recovery/stale/HUD/startup checks. Other endpoint and golden hashes remain unchanged.

## Hung By Son: stale-positive monster HP and auto-target verification (2026-10-05)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,286,762 bytes, SHA-256 `74ADCC563B0E67DA63F4F2B242E79911A93D0A2FFE94538E7DFBCAD0C8CE2D38`. Stage/logs: `build/hung-investigate-stale-target-20261005-b/`; verified backup `../jar/backup/Hung_By_Son_investigate-before-stale-target-fix-20261005.jar`, SHA-256 `6C7C3B32C35920B069AE103F41C82575D1F04E66293B5492F192EFA852EC81F9`. Atomic replacement succeeded; other endpoint/golden hashes unchanged in `publish.json`.
- User confirmed remote respawn recovery worked, then reported quái still visible with positive HP after another player killed it; auto kept attacking without HP loss until respawn. The inspected combat receiver returns before decoding victims when the attacker object is missing or removed. That client path can discard authoritative victim HP. A monster-only fallback at that exact return now fully decodes victim IDs/damage/HP/effects and footer before applying HP to previously confirmed current objects. It rejects truncated/oversized payloads, negative HP and values above native maximum; PK and the loaded-attacker animation path remain unchanged. This identifies a client-side cause without assuming the unrelated local server implements the remote server protocol; live packet causation has not been captured.
- Adds a transient primary-target watch for Auto. It counts only complete opcode6 serialization followed by successful native enqueue, not cast attempts, opcode9/PK, Auto OFF, empty vectors, malformed DTOs or failed enqueue. Three sent requests and >=2500ms with unchanged HP trigger an identity-scoped temporary exclusion and clear only the matching retained target, then query request7 through the shared cache queue. HP progress/fresh validated detail resets counting. Positive unchanged HP waits 3s before retry; positive progress releases immediately; HP0 passes to existing death/respawn monitoring. The receive fallback uses server HP, not estimated damage.
- Health verification has its own pending kind: it never calls the death quarantine bridge, sets HP to zero, freezes animation or owns the native object. All query results retain current-context/identity/template/level/position/HP validation and 400ms global pacing / two requests / 8s timeout. Mismatch, replacement, timeout and enqueue/query errors remove the health-query entry and expire temporary exclusion; no permanent pending wedge or persistent ban. At most 32 exclusions are held in memory, cleared across endpoint/map/khu changes; RMS formats and suite identity are unchanged. Auto target scan/retention and both secondary-vector branches compose the watch exclusion with the saved name/level filter.
- Passed: 74 stale-target host checks (count/time thresholds, quiet windows, HP progress, read-only query, positive/zero results, cooldown, timeout/mismatch cleanup, shared normal-recovery request queue, Auto/PK/scope/identity, bounded exclusions, fully parsed unseen-combat payload/truncation); 15 native checks (actual successful/failed serializer+enqueue, unsigned first target in a multi-target vector, Auto OFF/PK/empty/bad DTO exclusion, actual unseen-attacker receive HP0/effects/footer, PK bypass/truncation/native bounds); all prior 111 cache, 46 selection, 18 native recovery, HUD and combat/tempo/Noclip/local MOD/enhancement/NPC/trade/semantic/controller/route checks; isolated 12-second headless startup.
- Eleven native classes change only by explicit hooks/gates and 16 scoped RMS calls; independent inverse canonical parity passes. Adds 21 classes / 242 total, including three watch support classes; 2071 new member references verified. Native majors remain 48, support <=51. Original resource allow-list is still only the three Investigate manifest identity attributes; auto500/nativeInterval+300 remain unchanged. Input stable Hung SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F` remains unchanged.
- Source: `src/luna/features/MonsterTargetWatchSupport.java`, updated `MonsterCacheSupport.java` / `MonsterCacheAccess.java`; boundary `tools/luna/HungBySonStaleTargetPatch.java` and composed selection gate; fixtures `HungBySonStaleTargetHostTest.java`, `HungBySonStaleTargetBinaryTest.java`; catalog `symbols/hung-investigate-stale-target-20261005.properties`. Rebuild: `tools/luna/build-hung-investigate.ps1`. [Test instructions](HUNG_BY_SON_INVESTIGATE.md).
- This build has not yet been tested on the live server. A successful enqueue does not prove server acceptance; unchanged HP can also reflect lag or zero damage. Temporary rotation and revalidation reduce wasted auto attacks but do not repair remote-server state or guarantee its packet7 data is fresh. No local server code was read or changed for this fix.

## Hung By Son: recover remote monster death/respawn (2026-10-05)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,278,426 bytes, SHA-256 `6C7C3B32C35920B069AE103F41C82575D1F04E66293B5492F192EFA852EC81F9`. Stage/logs: `build/hung-investigate-respawn-20261005-c/`; verified backup `../jar/backup/Hung_By_Son_investigate-before-respawn-fix-20261005.jar`, SHA-256 `C7A558BF9CA4B590244C2388CAD7BD4B175ECC2269EA4D358252D89C2FC42987`. Atomic replacement succeeded; other endpoint/golden hashes unchanged in `publish.json`.
- User confirmed the prior cache/menu fixes worked, then reported remote monsters stopped appearing after another player killed them. Client inspection found two unhandled states: normal death leaves an existing object hidden (`dk`, `cG=4`), while special death/packet 8 can remove an object. The old cache only attempted restoration once per map and did not revisit confirmed IDs; its confirmation bridge also did not clear the hidden/death animation flags. The unknown-attacker native death helper takes a different animation path when the other player is not loaded. No live packet trace was captured to prove one exact opcode caused the reported occurrence.
- The semantic cache now monitors confirmed IDs from both cached and native loading (8 records/tick, each checked at most once per second). After 1500ms dead/absence grace, it borrows an existing dead object or restores one missing object/tick, then uses the existing 400ms global request pacing / two requests per cycle / 8s timeout. Valid HP0 replies remain dead and are queried again after 3s without consuming the error budget. Positive valid details clear hidden/death counters and apply the server position while keeping current/max HP exactly as received; late positive details for the same confirmed identity are also reconciled.
- The native death/respawn method has one reversible entry guard for active pending candidates or tracked confirmed hidden objects. Death animation can finish before the hidden hold; native timer prediction cannot fill HP while these objects await confirmation. Untracked native entities keep their original lifecycle. Timeout/transport/restore failures back off 20s/40s and suspend after three consecutive failures; mismatch is suspended until authoritative packet4 or a new map session. Borrowed native objects are never discarded as cache-owned placeholders. Packet4/state127 is now correctly treated as a transient animation/movement event, preserving an existing cache record instead of deleting it.
- RMS cache/selection formats and suite identity remain unchanged. Build still uses stable `Hung_By_Son.jar` SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F`, which remains unchanged. Ten native classes have only verified cache/UI/combat hooks and 16 scoped RMS rewrites; independent inverse canonical parity passed. Adds the same 18 classes, 239 total, native major 48/support <=51; original resource changes remain the three Investigate manifest identity attributes.
- Passed: 111 host cache checks (dead/missing recovery, repeated HP0 cycles, late HP-positive detail, ownership, timeouts/backoff, mismatch, ready/scope isolation, transient127, persistence/bounds); 18 native recovery checks using the actual unknown-attacker death handler, native hidden death/held timer, HP0→37 revival, packet8 marking, modeled world removal and replacement, unrelated special death; 46 selection host checks and native popup/multi-target checks; HUD toggle; 239 class links / 1655 new member references; all prior combat/tempo/Noclip/local MOD/enhancement/NPC/trade/semantic/controller/route checks and 12-second headless startup. The fixture invokes packet8 but models subsequent world removal; it does not run a complete logged-in world.
- Source/API: `src/luna/features/MonsterCacheSupport.java`, `src/luna/api/MonsterCacheAccess.java`; native bridge/hook: `tools/luna/HungBySonInvestigatePatcher.java`; new fixture `fixtures/hung-by-son/HungBySonMonsterRecoveryBinaryTest.java` plus expanded cache fixture. Catalog: `symbols/hung-investigate-respawn-20261005.properties`. Rebuild: `tools/luna/build-hung-investigate.ps1`. [Behavior/test instructions](HUNG_BY_SON_INVESTIGATE.md).
- This recovery build has not yet been tested on the live server. It depends on server responses for already-known IDs in the same endpoint/map/zone; it does not discover unknown monsters or reuse IDs across zones. Previously removed cache records must be learned again normally.

## Hung By Son: fix native monster-choice clicks and QCache visibility (2026-10-05)

- Published `../jar/Hung_By_Son_investigate.jar`, 1,274,355 bytes, SHA-256 `C7A558BF9CA4B590244C2388CAD7BD4B175ECC2269EA4D358252D89C2FC42987`. Stage/logs: `build/hung-investigate-popup-hud-fix-20261005-a/`. Verified preimage backup: `../jar/backup/Hung_By_Son_investigate-before-popup-hud-fix-20261005.jar`, SHA-256 `02591423334BBE33E0E17DB58223F709F2986CEBF1F17C03942D057BE4DDACC9`. Atomic replacement succeeded; other endpoints and golden hashes unchanged in `publish.json`.
- The native mode-0 popup dispatcher calls only `a(II)` with row `e/f` bytes; it bypasses `bt.a()` and ignores its Object payload. The earlier generated payload rows therefore stayed at “all”. Rows now use the native two-int constructor; the generated callback handles choice action 43 and decodes the row byte unsigned. Selecting/unselecting groups immediately updates the markers and persistent map profile, including rows 128/129. Native popup input/cleanup, other menus and packet formats are unchanged.
- QCache's generated drawing bridge checks the same public native flag used by MOD's “Hiện Thông Tin” action -77. OFF skips the entire drawing boundary (text/background); ON restores it. Cache collection, persistence, confirmation and combat filters continue independently. Application identity, RMS namespace and formats remain compatible with existing Investigate data.
- Passed: 55 cache and 46 selection host checks; revised selection fixture reproduces the previous failure, then exercises the actual popup dispatcher, multi-select/unselect, both markers and unsigned rows 128/129; keyboard/touch dispatch sites verified; native info handler ON/OFF/ON with 9 HUD checks. Archive/inverse-native verification, 239 class links / 1342 new member references, RMS/bridge checks, all combat/tempo/Noclip/local MOD/enhancement/NPC/trade/semantic/controller/route regressions and 12-second headless startup passed. Native class count/scope and original resource allow-list remain as in the selection stage below.
- Source: `tools/luna/HungBySonMonsterSelectionPatch.java`, `HungBySonInvestigatePatcher.java`; fixtures: `HungBySonMonsterSelectionBinaryTest.java`, `HungBySonInvestigateHudBinaryTest.java`. Catalog: `symbols/hung-investigate-popup-hud-fix-20261005.properties`; rebuild: `tools/luna/build-hung-investigate.ps1`. [Test instructions](HUNG_BY_SON_INVESTIGATE.md). These two corrections have not yet been tested in live-server gameplay.

## Hung By Son: staged monster selection + multi-zone cache (2026-10-05)

- User tested this staged version and reported group clicks remained at “all”; its previous fixture used `bt.a()` instead of the mode-0 popup dispatcher. A later correction uses the native two-int callback and tests that real dispatcher. The user also requested QCache follow MOD's existing information toggle. This stage is retained for comparison and is not the corrected artifact.
- Verified candidate: `build/hung-investigate-selection-20261005-c/Hung_By_Son_investigate.jar`, 1,274,325 bytes, SHA-256 `3ED678DFC369C32AE1D7D9614EAA57236D1180224F65CE3F0F184F5BAEC537F4`. Publication was attempted but Windows denied atomic replacement because the destination was open in another process; this stage was not published to the release root. Verified preimage backup: `../jar/backup/Hung_By_Son_investigate-before-monster-selection-20261005.jar`, SHA-256 `02591423334BBE33E0E17DB58223F709F2986CEBF1F17C03942D057BE4DDACC9`. Superseded by the corrected publication above.
- The previous cache already used endpoint/map/zone. This version verifies independent learned-zone records (including the same ID with different templates/positions), retains up to 128 recent contexts instead of 32, and evicts old contexts to keep the existing 128KiB budget. Unknown zones still require initial collection. Cache version, application name/vendor and RMS namespace remain compatible with the prior Investigate suite.
- Adds `Cài đặt cấu hình → Auto Tự Đánh → Chọn quái`: native multi-choice rows grouped by exact name + level. Selection persists per endpoint/map, shared across its zones. Empty/unconfigured selections allow all monsters. Filters nearest/retained auto targets and primary/both secondary target-vector branches before DTO allocation; unselected targets do not consume slots. Auto OFF and player targets pass through the selection policy. Changing selection clears an excluded retained auto target.
- Build uses unchanged `../jar/Hung_By_Son.jar` SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F`. Native changes: nine classes, exact cache/UI/target hooks and 16 RMS rewrites, reversible independent canonical comparison. Adds 18 classes; 239 total, native major 48 and support <=51. Original resources only differ in the three Investigate manifest identity attributes. Auto 500ms and combat nativeInterval +300ms remain verified.
- Passed: 55 cache and 46 selection host checks; 239 class links / 1341 new member references; actual native menu clicks, retained-target clearing, primary rejection and both secondary vectors, default other-map / Auto OFF / PK behavior, independent actual cache and selection RMS, Integer payload index 129; all previous combat/tempo/Noclip/local MOD/enhancement/NPC/trade/semantic/controller/route checks; isolated 12-second headless emulator startup. Logs and `verified.json` in the candidate stage above.
- New source/API: `MonsterSelectionAccess.java`, `MonsterSelectionSupport.java`; generated boundary tool `HungBySonMonsterSelectionPatch.java`. Host/binary fixtures: `HungBySonMonsterSelectionHostTest.java`, `HungBySonMonsterSelectionBinaryTest.java`. Rebuild: `tools/luna/build-hung-investigate.ps1`; generated catalog currently in the stage. [Behavior/test instructions](HUNG_BY_SON_INVESTIGATE.md).
- User confirmed initial cache restored known-zone monsters, then tested this selection stage and reported the popup click failure described above. Server-driven splash damage beyond the submitted target list remains unmeasured.

## Hung By Son: investigate persistent monster cache (2026-10-05)

- Published the explicitly requested parallel artifact `../jar/Hung_By_Son_investigate.jar`, 1,258,771 bytes, SHA-256 `02591423334BBE33E0E17DB58223F709F2986CEBF1F17C03942D057BE4DDACC9`. Input `../jar/Hung_By_Son.jar` remains SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F`. All original endpoint/golden hashes were checked; publication record `build/hung-investigate-final-20261005/publish.json`. No release was replaced, so no preimage backup was needed.
- Variant identity: `Hung_By_Son Investigate`, vendor `Hung_By_Son_Investigate`; all 16 direct native RMS open/delete/list calls use namespace `inv_`. Scoped enumeration returns logical names and cannot enumerate/delete the original suite's records. Cache is initially empty and remains separate from native login/config records.
- Eight native classes change only by explicit object/detail/map/tick/paint/endpoint/destroy hooks and RMS-boundary rewrites; independent inverse canonical comparison passes. Add nine support/interface/bridge classes: 999 baseline ZIP entries +9 classes, 230 total classes. All native majors remain 48; added majors <=51. Only manifest identity attributes change among original resources; icon/main MIDlet class remain unchanged. Combat's 500ms auto gate and nativeInterval +300ms, PK/full-map filters, packet formats and other native methods remain preserved.
- The semantic feature has bounded persistence and no MIDP imports; generated boundary supplies native lookup/factory/request and RMS/HUD. Native packet 7 hook runs after HP/maxHP/cX writes. Cached IDs are converted back to native signed shorts at the bridge boundary. Pending placeholders are excluded from auto and native retries, then queried once per >=400ms, max twice each, with an 8s deadline from first query. Only known same-context IDs are queried; there is no enumeration scan.
- Passed: 46 cache lifecycle/persistence/context/queue/mismatch/timeout/corruption/bounds checks; exact archive CRC/topology and reversible native scope; 230 no-init class links and 850 new member-reference checks; actual shared-memory RMS namespace/delete/restart tests and generated bridge pending/live/dead checks; 43 combat gate, 93 tempo clicks, 24 Noclip clicks, 349 collision, 94 local MOD, 13 enhancement-wait, 17 NPC-session checks; native local trade regression; semantic/controller/route/combat/material fixtures; isolated 12-second headless emulator startup with empty stderr. The existing local-services fixture accepts an explicit expected class count for this nine-class variant, while its default remains 221.
- Sources: `src/luna/api/MonsterCacheAccess.java`, `src/luna/features/MonsterCacheSupport.java`, `tools/luna/InvestigateStorage.java.in`, `HungBySonInvestigatePatcher.java`, `build-hung-investigate.ps1`; fixtures `HungBySonMonsterCacheHostTest.java`, `HungBySonInvestigateBinaryTest.java`; catalog `symbols/hung-investigate-20261005.properties`. Stage/logs: `build/hung-investigate-final-20261005/`. Earlier incomplete stages are retained under build only and were not published.
- Live server response, ID stability and rendered in-game HUD/gameplay remain untested. Cache capacity is 256 records per context, 32 recent contexts, <=128KiB. No cross-zone ID reuse; the same-layout assumption is not treated as proof of instance identity. Graceful exit flushes; force-kill may lose up to the last 500ms of new records. [Behavior and test instructions](HUNG_BY_SON_INVESTIGATE.md).

## Hung By Son: auto gate 500ms (2026-10-05)

- Published `../jar/Hung_By_Son.jar`, SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F`. Input SHA-256 `D917DA2442C8AED8A67F4D30BD5EC2F462B3883D0319827ACA684EAF05204005`; verified backup `../jar/backup/Hung_By_Son-before-auto500-20261005.jar`. Candidate/logs/manifest: `build/hung-auto500-verified-20261005/`; atomic publication and other endpoint/golden checks: `publish.json`.
- User replaced the proposed native-combat/no-delay variant with a request to increase only the existing auto gate by 100ms. No `Hung_By_Son_no_delay.jar` was created. One `SIPUSH` initializer changes 400 → 500; comparison remains strict `elapsed > 500ms`. Request comparison remains strict `elapsed > nativeInterval +300ms`. All other cast methods, animation, server-provided cooldowns, packet format, full-map targets, PK controls, Auto Login, tempo, Noclip, manifest and resources are preserved.
- Maintained source: `tools/luna/HungBySonAutoGatePatcher.java`, `build-hung-auto500.ps1`; fixture: `fixtures/hung-by-son/HungBySonAutoGateHostTest.java`; catalog: `symbols/hung-auto500-20261005.properties`. Rebuild from the pinned backup with `tools/luna/build-hung-auto500.ps1 -InputJar ../jar/backup/Hung_By_Son-before-auto500-20261005.jar -DependencyDir <external-dependencies>`; the script stages and verifies without publishing.
- Passed: inverse canonical comparison proving one initializer change only; 43 runtime/method checks including initialized 400/500ms gates and strict 499/500/501ms boundaries; unchanged request method; 999 entries/221 linked classes/731 resources, class versions and archive CRC; 349 collision checks, 24 Noclip clicks, 93 tempo clicks; 94 local MOD, 13 enhancement-wait and 17 NPC-session checks; native V3 trade regression; semantic/controller/route/combat/material fixtures; isolated 12-second headless startup with empty stderr. Auto Login class remains byte-identical to the input. The earlier Auto Login-specific archive fixture is scoped to a pre-Noclip input and cannot be applied directly to this release; its behavior was not rerun here.
- Live combat, XP per hour, server acceptance and a 400ms-versus-500ms gameplay comparison have not been measured. Gate 500ms is a readiness threshold, not a guaranteed packet or damage cadence.

## Hung By Son: menu Noclip mặc định OFF (2026-10-04)

- Published `../jar/Hung_By_Son.jar`, 1,238,289 bytes; SHA-256 `D917DA2442C8AED8A67F4D30BD5EC2F462B3883D0319827ACA684EAF05204005`. Input SHA-256 `97DB01AD7E5550D4B8B5D747C7D0F870CF21972F03E9ABBEAB757D2DC3AEDACC`; verified backup `../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar`. Staged artifact and logs: `build/hung-noclip-verified-20261004/`. Atomic replacement, preimage, seven other endpoint hashes and golden baseline verified in `publish.json`.
- The MOD root menu adds one `Noclip: [OFF]` / `Noclip: [ON]` row after game speed. Native action 106 toggles the state and rebuilds the root menu. Starts OFF in a fresh client; no RMS persistence. The selected state remains in the running client across map changes.
- One tile-query hook and two pathfinder collision-read hooks convert blocked values 1/-1 to 0 only while enabled. Original collision arrays remain unchanged; native map-boundary returns remain in force. Native key collision and route search use the toggle. No server packets, movement cadence, combat or Auto Login methods change.
- Exactly three classes change: map, native world/pathfinder, MOD menu. All 999 entries, 221 classes (major 48), 731 resources and manifest are preserved. Sources: `tools/luna/HungBySonNoclipPatcher.java`, `HungBySonNoclipMenuPatch.java`, `build-hung-noclip.ps1`; fixtures `fixtures/hung-by-son/HungBySonNoclipCollisionHostTest.java`, `HungBySonNoclipMenuHostTest.java`; catalog `symbols/hung-noclip-20261004.properties`.
- Passed: 349 native collision/path tests including OFF differential against input, four key directions, raw-array preservation and boundaries; 24 actual Noclip button/callback checks; 93 actual tempo checks; ZIP CRC, class/reference linking with `-Xverify:all`; five semantic/controller/route/combat/material suites; local services, enhancement wait/NPC session and V3 trade regressions; isolated 12-second headless emulator startup with empty stderr.
- Rebuild with `tools/luna/build-hung-noclip.ps1 -InputJar ../jar/backup/Hung_By_Son-before-noclip-menu-20261004.jar -DependencyDir <external-dependencies>`. The script stages and verifies without publishing. Live server movement acceptance and rendered in-game wall crossing remain untested. Server position corrections retain their native behavior.

## Hung By Son: Auto Login và hộp thoại hành trang (2026-10-04)

- Last verified/published: 2026-10-04. Artifact `build/hung-autologin-dialog-final-20261004/Hung_By_Son.jar`, published `../jar/Hung_By_Son.jar`, 1,236,829 bytes; SHA-256 `97DB01AD7E5550D4B8B5D747C7D0F870CF21972F03E9ABBEAB757D2DC3AEDACC`. Input/verified backup SHA-256 `D6F2489636C66EAE3AD1A959BB6FF4E428EE4BD16740EE2BBE22D575354DCFE1`; backup `../jar/backup/Hung_By_Son-before-autologin-dialog-fix-20261004.jar`. Atomic publication and all other endpoint/golden hashes verified in `build/hung-autologin-dialog-final-20261004/publish.json`.
- Bug: with Auto Login and Auto Tự Đánh enabled, the inventory tab plus an ordinary confirmation could reach the generic reconnect fallback after >40 scan ticks. Its direct dialog action `(0,0)` opened Login Screen. A confirmation containing `10`, including +10 equipment, could take another reconnect branch. Offline reproduction uses the actual Auto Login method and native dialog action; no account/network/RMS access.
- Fix: in the generic active-dialog fallback only, post-world gameplay UI with the existing connection lifecycle flag set returns without clicking the dialog; the scan counter and unknown-notice streak reset. Login, character selection and loading are excluded. The explicit loss-notice branch stays ahead of the guard, preserving reconnect when the transport flag is stale; retry deadlines, captured credentials and native post-world popup handling are unchanged.
- Scope: exactly one class and its tick method change; 10 other native methods and all field declarations are unchanged. All 999 entries, 221 classes (major 48), 731 resources, manifest and all other class bytes are preserved. Symbols: `symbols/hung-autologin-dialog-fixed-20261004.properties`; sources: `tools/luna/HungBySonAutoLoginDialogPatcher.java`, `build-hung-autologin-dialog-fix.ps1`, `fixtures/hung-by-son/HungBySonAutoLoginDialogHostTest.java`.
- Passed: 226 binary/behavior checks (old tick-41 login, fixed 1,000-tick ordinary/+10 dialogs, connected/disconnected loss handling, positive login and character-selection commands, authentication dialogs, counters/deadlines and native post-world popup); 93 actual tempo-menu click checks; ZIP CRC/resource/ABI/private-access checks and full `-Xverify:all` linkage; semantic/controller/route suites, 94 local MOD checks, 13 enhancement-wait checks, 17 NPC-session checks and local trade regression. Headless emulator startup: 12 seconds, fresh memory RMS, empty stderr. Reports/manifest/logs: `build/hung-autologin-dialog-final-20261004/`.
- Rebuild from the pinned backup using `tools/luna/build-hung-autologin-dialog-fix.ps1 -InputJar ../jar/backup/Hung_By_Son-before-autologin-dialog-fix-20261004.jar -DependencyDir <external-dependencies>`. The script stages and verifies a new artifact; it does not publish. Live Bạch Hổ gameplay and the exact modal from the user's original session have not been recorded.

## Hung By Son: ba cơ chế theo Bạch Hổ (2026-10-04)

- Last verified/published: 2026-10-04. Candidate `build/hung-bachho-final-20261004/Hung_By_Son.jar`, published `../jar/Hung_By_Son.jar`, 1,236,507 bytes; SHA-256 `5878D588F1CED165EE7045AAB656EBECD043FAA64556CC4CD317387D44C4727C`. Input SHA-256 `EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC`; verified backup `../jar/backup/Hung_By_Son-before-bachho-parity-20261004.jar`. Reference is the current Bạch Hổ row above, unchanged.
- Exact audited behavior: x1–x5 direct-selection submenu, default x1/no RMS; accumulator `2*elapsed*speed`, 40ms step, three-update cap, reset backlog at 120ms; movement mask 109/8 and clock refresh after every tick; paint-only 16ms pacing with minimum 1ms sleep. Monster scan/retention use 2,000,000, multi-target mark distance is zero, native monster filters/order are matched. Cast enters the native preparation/send block directly; auto gate is strict `>400ms`, request gate is strict `>nativeInterval+300ms`. No frame-1/frame-2 override; native animation and Hung PK controllers remain in use.
- Maintained sources: `tools/luna/HungBySonBachHoTempoPatcher.java`, `HungBySonBachHoCombatPatcher.java`, `HungBySonParityJarVerifier.java`, `build-hung-bachho-parity.ps1`; fixture `fixtures/hung-by-son/HungBySonBachHoParityHostTest.java`. Catalogs: `symbols/hung-bachho-tempo-20261004.properties`, `symbols/hung-bachho-combat-20261004.properties`. Build from the exact backup and unchanged reference with external ASM/MicroEmulator dependencies.
- Passed: 1,214 host/binary checks, including 360 differential loop cases with threshold/backlog/slow paint/update costs and per-tick clock traces, 16 actual movement ticks, runtime menu selection, near/far/dead/player/self/tie monster selection, exact auto/request boundaries and synchronous send. Native callback forwarding for submenu action IDs and reference menu caption/labels/markers were audited in `build/hung-bachho-parity-20261003/menu-topology.txt`.
- Archive checks: 999 entries, 221 classes, 731 resources, all class majors remain 48; exactly six existing classes change. Manifest/resources and archive topology are byte-identical; JVM `-Xverify:all` reflection linking and all new member-reference checks pass. Combat's unchanged-method scope is also checked canonically. Full manifest/report: `build/hung-bachho-final-20261004/manifest.txt`.
- Semantic/controller/route/combat/login-material fixtures pass. Hung local-services regression passes 94 checks, enhancement wait 13 checks, NPC session 17 checks, and native local-trade regression passes. Headless MicroEmulator observed for 12 seconds with isolated home/memory RMS, empty stderr and no startup exception. Atomic replacement, published hash/backup verification, unchanged other endpoints and immutable golden hash pass; publication record `build/hung-bachho-final-20261004/publish.json`.
- Not verified: live gameplay, rendered in-game interactions or accepted attack timing on the server. The 400ms auto gate does not override separate server/native skill cooldowns. Behavior details: [Hung behavior](HUNG_BY_SON_BEHAVIOR.md), [parity audit](HUNG_BY_SON_BACHHO_PARITY.md).

## Hung By Son: sửa callback tốc độ và thêm x0.8/x0.9 (2026-10-04)

- Candidate verified: `build/hung-tempo-fixed-final-20261004/Hung_By_Son.jar`, SHA-256 `D6F2489636C66EAE3AD1A959BB6FF4E428EE4BD16740EE2BBE22D575354DCFE1`, 1,236,717 bytes. Incremental input is the broken menu release `5878D588F1CED165EE7045AAB656EBECD043FAA64556CC4CD317387D44C4727C`; verified backup `../jar/backup/Hung_By_Son-before-tempo-menu-fix-20261004.jar`.
- **Published:** after the user closed the emulator and confirmed, atomic replacement of `../jar/Hung_By_Son.jar` passed. Its hash matches the candidate above. Windows had refused the first attempt because Java PID 8752, session 13, held the destination. No emulator was forcibly stopped. Backup hash verified; all nine endpoint/golden hashes were checked. Publication record: `build/hung-tempo-fixed-final-20261004/publish.json`.
- Runtime user screenshots exposed a gap in the earlier fixture: it invoked the menu handler with full integers and never traversed the button's signed-byte field. Native `bt` construction narrows -3101..-3105 to -29..-33; -29 opens “Nhập Key”, -31 reopens the submenu, and other values use unrelated paths. Action IDs are now 101..105. x4/x5 are replaced by x0.8/x0.9, in the order x1/x2/x3/x0.8/x0.9. Root caption and selected markers use decimal text.
- Fraction scaling carries the remainder of `elapsed*8/10` or `elapsed*9/10`, resets on speed changes and leaves combat's real-time clock unchanged. x1/x2/x3, full-map combat, cast gates, animation and other features are preserved.
- Source: `tools/luna/HungBySonTempoMenuFixPatcher.java`; actual click fixture `fixtures/hung-by-son/HungBySonTempoClickHostTest.java`. The maintained `build-hung-bachho-parity.ps1` now composes the menu repair before tests/output. A complete rebuild from the exact original backup produced the same candidate SHA as the incremental repair. Detailed catalog/report: `build/hung-tempo-fixed-final-20261004/tempo-fixed.properties`, `tempo-fix-report.txt`, `manifest.txt`.
- Passed: 93 native button/callback/marker checks with no activation-key popup and no packet sends; 4,828 parity/fraction checks, including 4,000 cumulative fractional elapsed checks and 216 donor loop comparisons. Archive topology/resources/manifest/class versions unchanged; 221 classes link with `-Xverify:all`. Incremental diff changes exactly menu and Canvas classes. Full semantic/controller/route/local-services/NPC/enhancement/trade checks pass; isolated 12-second emulator startup passes with empty stderr. Current endpoint/golden snapshot contains all nine hashes in `build/hung-tempo-fix-20261004/endpoints-before.json`.
- Not tested on a live server or through rendered gameplay in the user's emulator. Callback execution is a native bytecode host fixture; startup is a separate emulator smoke check.

## Hung_By_Son offline local activation (2026-09-30)

- The original `../jar/Hung_By_Son.jar` had SHA-256 `490D780C4D8C65A8F6B9DCE3E8F340650750F04FDC75EF43D193CEA3F76565C0`; it is preserved at `../jar/backup/Hung_By_Son-before-offline-local-20260930.jar`.
- The first patch (SHA-256 `375EB84D4B33197E1B743596E8D812863E1EB04DB8A6860776FDE6209E82097F`) only intercepted the Brain API. It left the separate license check active and returned a Brain response with too few fields. The user's runtime screenshot showed `Tu choi: Key khong ton tai!`. This intermediate version is preserved at `../jar/backup/Hung_By_Son-before-offline-local-v2-20260930.jar`.
- The offline activation version was SHA-256 `6E0111AAB7A9FD7AE4D8A42D675AE52D6EE89CE5179292D2DDCE3003298364E9`, built from the original backup via `tools/legacy/hsosky/HungBySonOfflinePatcher.java`. Build artifact: `build/releases/Hung_By_Son_offline_local_v2_20260930.jar`.
- The Brain HTTP POST helper returns a locally generated, syntactically valid `OK|300000|` response and never opens `/api/mod/brain/secret`. The separate Supabase license worker is not started; its POST method returns immediately. The client initializes all seven feature flags as enabled, clears the lock flag, handles key submission locally, and skips the lock overlay. The old expiry check is no longer reached through the license update or overlay.
- The license update no longer calls the method that wrote `status_<username>.txt` containing plaintext `game.user` and `game.pass`.
- The JAR still has 993 entries. The patcher checks both modified classes and its output structure. Runtime gameplay with an account has not been exercised after this correction. Actions whose actual command payload came from the Brain API may require a separate local implementation; this patch only provides an empty Brain payload.

## Hung_By_Son 1.5x frame rate and 12 skill slots (2026-09-30)

- Previous file: SHA-256 `E23F4080D681196048449959430E0AAF1883586EB0F4C6934D2B0E2E6E6E6494`; source was the offline activation version above. Backup: `../jar/backup/Hung_By_Son-before-1p5speed-12skill-20260930.jar` (SHA-256 `6E0111AAB7A9FD7AE4D8A42D675AE52D6EE89CE5179292D2DDCE3003298364E9`). Build output: `build/releases/Hung_By_Son_12skill_1p5speed_20260930.jar`.
- Main loop target is now 27, 27, 26 ms in rotation, averaging 26⅔ ms versus the original 40 ms (nominal 25 to 37.5 frames/s). The local player's initial x/y displacement executes on two of every three frame updates; other actor updates keep their original cadence. Actual rate depends on render time and emulator timing.
- Hotbar storage and its skill/item assignment menus now have 12 cells per page. The two drawing layouts, labels, touch width, keyboard input, and key state arrays were updated. New cells 6–12 use F/G/H/J/K/L/M. Existing 5-cell saves, and 10-cell saves from older builds, expand to 12 while preserving assigned cells.
- Build preserves the previous local activation changes. Archive entry count is 994 (original 993 plus `HungBySon12SkillSupport.class`); that helper uses Java 1.4 class format, matching the original client. The build patcher identified all target classes and completed its structural checks. Gameplay, touch use, and movement over a live server have not been exercised after this upgrade. On screens narrower than 296 logical pixels, cell backgrounds may overlap because the client uses 24-pixel hotbar art.

## Hung_By_Son FPS menu correction (2026-09-30)

- User runtime screenshot showed `Mức Fps: 30` and no apparent speed increase. The prior frame patch targeted 37.5 FPS relative to the original 25 FPS default while bypassing the menu FPS field. Relative to the user's displayed 30 FPS, that target was only 1.25x; the menu also stayed at 30.
- The FPS menu correction version was SHA-256 `8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4`. Build output: `build/releases/Hung_By_Son_12skill_fps45_20260930.jar`. The preceding version is backed up at `../jar/backup/Hung_By_Son-before-fps45-menu-sync-20260930.jar` (SHA-256 `E23F4080D681196048449959430E0AAF1883586EB0F4C6934D2B0E2E6E6E6494`).
- Default menu setting is now 45 FPS, exactly 1.5x the displayed 30 FPS baseline. The menu cycles 30 → 45 → 60, and the canvas loop reads that same FPS field each frame. Integer remainder accumulation produces a nominal average frame interval of `1000/FPS` ms. At 45 FPS, intervals average 22.22 ms. Local-player coordinate displacement is scheduled for 30 updates per second at each menu setting; the 12-cell hotbar and local activation remain present.
- Static bytecode inspection confirmed the 45 FPS default, 30/45/60 menu constants, the canvas helper, and 994 archive entries. Actual frame rate and movement have not been measured in a running emulator or against the server; rendering or emulator limits can keep the observed rate below the menu target.

## Hung_By_Son 300 ms cast interval (2026-09-30)

- The 300 ms cast version with default 45 FPS was SHA-256 `0044709A7214F02934EBCCD20C3CFA1167DC722BF3E6B303CDF5DFC4480C3FF4`. Build output: `build/releases/Hung_By_Son_fps45_12skill_cast300_20260930.jar`. Backup of the previous release: `../jar/backup/Hung_By_Son-before-cast300-20260930.jar` (SHA-256 `8BC1B4B85E602FC3554DB9415B343003755C5D750D0922D632309BF5E9FCCCF4`).
- `tools/legacy/hsosky/HungBySonCast300Patcher.java` moves local cast packet emission to attack animation frame 1, releases the local actor from attack recovery at frame 2, and changes both the cast request interval and auto-combat gate to 300 ms. The cast controller is shared by attacks on monsters and players. Server-provided per-skill cooldowns and server acceptance are unchanged.
- The archive retains 994 entries. A bytewise comparison of decompressed entries against the preceding release found exactly two changed classes (cast controller and animation controller); all other entries, including the 12-cell hotbar and FPS changes, are identical. The patcher's structural checks completed. No running-game or server measurement of repeated casts has been made.

## Hung_By_Son default 30 FPS (2026-09-30)

- The default 30 FPS version was SHA-256 `26CD4D35C1A2141D38AA9391DB5212F1A67DA4E7DBBD0FE7E3C21146D9FA35FA`. Build output: `build/releases/Hung_By_Son_12skill_fps30_cast300_20260930.jar`. Previous JAR backup: `../jar/backup/Hung_By_Son-before-default-fps30-20260930.jar` (SHA-256 `0044709A7214F02934EBCCD20C3CFA1167DC722BF3E6B303CDF5DFC4480C3FF4`).
- FPS starts at 30 after launch; the menu still cycles 30 → 45 → 60. The canvas fallback is also 30. Local-player displacement remains scheduled for 30 updates per second at each setting, and the 300 ms cast interval, 12-cell hotbar, and local activation remain present.
- The archive retains 994 entries. Comparing decompressed entries with the previous release found only two changed classes (canvas timing and FPS initialization); all other entries are byte-identical. Bytecode inspection found the initial FPS value 30. Running-game timing has not been measured.

## Hung_By_Son 500 ms enhancement intro (2026-09-30)

- The 500 ms intro version was SHA-256 `EB19D83DE2A7B59BE045E01ED0DA14B9213904A7015CB2F3426611413DF13764`. Build output: `build/releases/Hung_By_Son_intro500_result_native_20260930.jar`. Backup of the previous JAR: `../jar/backup/Hung_By_Son-before-intro500-20260930.jar` (SHA-256 `26CD4D35C1A2141D38AA9391DB5212F1A67DA4E7DBBD0FE7E3C21146D9FA35FA`).
- `tools/legacy/hsosky/HungBySonEnhancementIntro500Patcher.java` changes all six shared enhancement-screen intro thresholds from 3700 ms to 500 ms and uses a `>= 500 ms` transition check. The existing success and failure result stages are unchanged. Once the server result selects stage 3 or 4, the client can enter that stage at the 500 ms mark; a later server result still determines when the matching visual can begin.
- The archive retains 994 entries. Comparing decompressed entries with the previous release found exactly one changed class, the enhancement screen; all other entries are byte-identical. The patcher confirmed six new intro thresholds. The animation has not been observed in a running game.

## Hung_By_Son automatic ghost captcha (2026-09-30)

- The ghost captcha version was SHA-256 `FAA8DA06D77653BC0DE87F99D92E31016A9AF46CC73735E62CCFB7ED1232786F`. It was restored on 2026-09-30 after the Auto Farm menu rollback below. Build output: `build/releases/Hung_By_Son_ghost_auto_20260930.jar`. Backup of the previous JAR: `../jar/backup/Hung_By_Son-before-ghost-auto-20260930.jar` (SHA-256 `EB19D83DE2A7B59BE045E01ED0DA14B9213904A7015CB2F3426611413DF13764`).
- `tools/legacy/hsosky/HungBySonGhostPatcher.java` ports the ghost captcha logic of `tools/legacy/ghostmod/GhostMod.java` to this client. No menu entry was added; the feature is always active. The patcher locates the network branch that stores the image for message id 9999 in the ghost captcha field, and hooks `HungBySonGhostSupport.onCaptcha` after that store. It rewrites two support placeholders into bridges: the native ghost answer sender (opcode -28, the same sender the skill-key path uses for a focused `cy==2` ghost) and a null check on the captcha field. Lookup is structural; the patcher stops if any symbol is missing or ambiguous.
- Build: `javac -encoding UTF-8 --release 7 -cp microemulator.jar` for `HungBySonGhostSupport.java`, then the patcher (`input.jar output.jar HungBySonGhostSupport.class`). The support class is written as Java 1.4 format and contains no `StringBuilder` or `invokedynamic`.
- The archive has 995 entries (994 + `HungBySonGhostSupport.class`). A decompressed-entry comparison against the backup found exactly one changed class (the network message handler) and an identical manifest. JVM `-Xverify:all` linking and initialization of the support class and the patched handler passed. A harness ran the published support bytes against stub classes with the client's symbol names and synthetic captcha images. It confirmed OCR of 13579/97531/55197, 1.5 s key spacing, one 20 s retry, immediate answer to a new captcha after a clear, idle exit, and no second press on repeated delivery. The OCR thresholds have not been checked against a real server captcha, and no answer has been observed in a running game or on a server.

## Hung_By_Son Auto Farm menu (2026-09-30, rolled back)

- Rolled back at the user's request: the user had forgotten how to use the original menu, and the change was not needed. `../jar/Hung_By_Son.jar` was restored from `../jar/backup/Hung_By_Son-before-autofarm-menu-20260930.jar`. The rolled-back JAR is kept at `../jar/backup/Hung_By_Son-before-rollback-autofarm-menu-20260930.jar`.
- The rolled-back version was SHA-256 `5AA61C306EEE41C02B1137BD833422EE76173F961C415BD188D31B479C8637CE`. Build output: `build/releases/Hung_By_Son_autofarm_menu_20260930.jar`. Backup of the previous JAR: `../jar/backup/Hung_By_Son-before-autofarm-menu-20260930.jar` (SHA-256 `FAA8DA06D77653BC0DE87F99D92E31016A9AF46CC73735E62CCFB7ED1232786F`).
- Before this change, the MOD HungDZ row `Auto Farm: [BẬT/TẮT] >` (action -40) only toggled the farm flag and reopened the main menu. The farm settings screen (-39, formerly titled "Cài Đặt Cấu Hình") could only be reached through a separate main-menu row added by the farm module.
- `tools/legacy/hsosky/HungBySonAutoFarmMenuPatcher.java` changes the main row to a constant `Auto Farm >`. Action -40 now opens the settings screen, retitled `AUTO FARM`. A new first row `Auto Farm: [BẬT/TẮT]` (new action -49) toggles the flag and applies and saves it through the module's existing methods. It shows `Auto Farm: Đã BẬT/TẮT` and refreshes the screen. The farm module's separate `Cài Đặt Cấu Hình` main-menu row was removed. The patcher locates the module, flag, settings builder, main builder and notice helper from the -40/-39 branches, and it checks that -49 was unused by every menu item and handler.
- The archive retains 995 entries and the manifest is identical. Exactly two classes changed: the MOD main-menu class and the Auto Farm module. JVM `-Xverify:all` linking and initialization of both classes passed. The menu has not been opened in a running game.

## Hung_By_Son HSO Luna XP popup (2026-09-30)

- The XP popup version was SHA-256 `41C720A1584C55F5BB2047CC0C4CF8907ECC63D8AD8383B4900281B06EB7517C`. Build output: `build/releases/Hung_By_Son_xp_luna_20260930.jar`. Source was the ghost captcha version (the Auto Farm menu change is not included). Backup of the previous JAR: `../jar/backup/Hung_By_Son-before-xp-luna-20260930.jar` (SHA-256 `FAA8DA06D77653BC0DE87F99D92E31016A9AF46CC73735E62CCFB7ED1232786F`).
- `tools/legacy/hsosky/HungBySonXpPopupPatcher.java` ports `tools/luna/XpPopupPatcher` to this client. Classes and fields are located structurally from the popup constructors, the type switch, the base update and the font initializer. Packet 30 creates type-1 text `<integer>xp`. Type 1 now draws with a generated 2x bold-green font built in the same way as Luna's. Its launch uses Luna's native type-1 values scaled by the Luna profile: vx = -rand(1,3), vy = -rand(8,12)·4/9, lifetime = rand(20,25)·9/4. Gravity is +1 on 4 of every 9 ticks. Luna's sign helper is called with (0,1) and always takes the negative branch, so this port also drifts left. Other popup types keep this client's previous motion.
- The archive has 997 entries: 995 + `x1/mfont/tahoma_14b_green.png` + `mfont/tahoma_14b`. Both added resources are byte-identical to those in `../jar/luna_final.jar`. The manifest is identical, and exactly two classes changed: the popup and the font class. JVM `-Xverify:all` linking and initialization of both classes passed. A JVM harness created 3000 type-1 popups in `luna_final.jar` and in this build and stepped their native update. Both give lifetime 45..54 ticks, peak rise 3..6 px, drop at expiry 330..558 px and x drift -108..-45 px, with an identical sample trajectory. The previous build rose 48..93 px in a straight line for 24..31 ticks. Types 2–6, 8–11 and -3 produced the same statistics as the previous build. The font rendering and the animation have not been observed in a running game. Ticks follow the selected FPS, so wall-clock duration at 30/45/60 FPS differs from Luna's frame rate.

## Hung_By_Son Auto Di Buon V3 without the licence server (2026-09-30)

- The V3 local version was SHA-256 `B03DBCF4DF77E9AA995D2EA252B19FB81CA38BA38BA468221500B87FF5346C1D`. Build output: `build/releases/Hung_By_Son_v3_local_20260930.jar`. Backup of the previous JAR: `../jar/backup/Hung_By_Son-before-v3-local-20260930.jar` (SHA-256 `41C720A1584C55F5BB2047CC0C4CF8907ECC63D8AD8383B4900281B06EB7517C`).
- Scope: the V3 module's menu and state machine are unchanged. The offline activation patch had left all five server "secrets" (packet formulas) empty, so V3 could not summon, buy or revive. `tools/legacy/hsosky/HungBySonTradeLocalPatcher.java` compiles `HungBySonTradeLocal.java.in` against the input JAR. It stores the class as version 48 and hooks four places: the formula getter (`formula`), the key-verified check (`handles`), the menu-open dispatcher (`onMenu`) and the shop constructor (`onShop`). V3 always passes a null argument array, so each formula is built from live client data, following HSO Luna. All opcodes are those of HBS's own native senders, which match Luna: 4, 5, 23, 24, 31, 32 and -30.
  - `buon_tay.thu_len` / `thu_xuong`: `ep|4|s<x>;s<y>&&ep|32|s<id>` (position sync, then use the item). The item is taken from the consumable bag (category 4). Its normalised name must start with "bo" (Bò), "lac da" (Lạc Đà) or "lua" (Lừa). A missing item gives a notice. Dismissing reuses the same item, as Luna does.
  - `buon.mua_gem`: the first time on each map, it requests the nearest NPC (op 23 + op 5, like V3's sell opener). After 1.5 s it presses the "Mua bán"/"Cửa hàng" row through the native menu box. It then records the shop type (`bw`) and the Kim cương/Ruby/Saphia ids by name from the shop constructor, ignoring bag vectors, and returns to the game screen. After that it sends `ep|24|b<type>;s<id>;s1`, at most once every 2 s.
  - `buon.hoi_sinh`: `qn|-51|0|0` with a revive ticket, otherwise `ep|31|b0`. This is the same choice as HBS Auto Hồi Sinh.
  - `buon.ban_gem`: it deliberately returns a non-formula. V3 then uses its existing "Bán đá quý" menu fallback; `handles` marks the key as verified so the fallback runs.
- Verification: the archive has 998 entries and exactly three classes changed (formula service, mod menu, shop screen) plus the support class. The manifest is identical. JVM `-Xverify:all` linking and initialization passed. A harness allocates the real game classes. It checked all formulas, including the "Tơ lụa" false-match guard, the per-map shop capture and the buy pacing. It also ran them through V3's real executor. The game sender logged `op 23 [-57]` and `op 5 [-1 -57]` when the shop was opened. Not yet observed on a live server: the item names for Lừa/Lạc Đà/Bò, whether reusing the item dismisses the animal on this server, whether op 24 is accepted, whether the NPC menu row is "Mua bán", and the return to the game screen.

## Hung_By_Son Bạch Hổ server addition (2026-10-02)

- Last verified: 2026-10-02. The Bạch Hổ addition version was SHA-256 `786E33B12A4EA85290A88CD83AE19C4A855200A5577C2D7B79FF1BAD970EF535`. Build output: `build/releases/Hung_By_Son_bach_ho_complete_20261002.jar`; verification/manifest record: `build/server-list-20261002/release-verification.txt`.
- Source target: SHA-256 `B03DBCF4DF77E9AA995D2EA252B19FB81CA38BA38BA468221500B87FF5346C1D`, preserved at `../jar/backup/Hung_By_Son-before-bach-ho-20261002.jar`. Donor: `../jar/HungSon-update.jar`, SHA-256 `DC2E35044C141BF03F63E6AACD6E9BA1264A14CCA7FCE5C6D14ACF24F76D50B0`; donor is unchanged.
- `tools/legacy/hsosky/HungBySonServerListPatcher.java` locates the Vietnamese server list structurally, reads the exact donor row **Bạch Hổ / hs8.teamobi.com**, and appends it at target index 9. The nine existing rows retain their indices, including Server Lậu at index 8 and its native connection handling. Both parallel metadata arrays grow from 9 to 10 entries; the new port 19129 and language 0 come from donor index 8. DNS lookup on this date returned `103.77.166.204`; the JAR stores the donor hostname, allowing DNS updates.
- Verification passed: initialized server configuration has 10 rows; all prior rows and metadata values are unchanged; ZIP CRC and entry comparison retain 998 entries, 220 classes, identical resources and manifest, and identical class versions. Exactly two classes change (server configuration and metadata holder). JVM `-Xverify:all` loading and reflection linking of all 220 classes passed. The patcher uses the original constant pools and preserves existing instructions except the list and metadata additions.
- No emulator gameplay or real-server selection/login was performed. Luna-specific controller and legacy-bridge tests do not apply to this separate Hung_By_Son binary; no combat, trade, packet, or controller code was modified.

## Hung_By_Son complete local MOD services (2026-10-02)

- Last verified: 2026-10-02. The first complete-local-services version was SHA-256 `8299D3CBA6B96E8605D745A6BCAAC5F3A4F9A0AD6DF914AA1D7A3412373BBAF6`. Source target: the Bạch Hổ version above, SHA-256 `786E33B12A4EA85290A88CD83AE19C4A855200A5577C2D7B79FF1BAD970EF535`. Backup/rollback: `../jar/backup/Hung_By_Son-before-full-offline-20261002.jar`.
- Build: `tools/legacy/hsosky/HungBySonFullOfflinePatcher.java` and `HungBySonOfflineLocal.java.in`, producing `build/releases/Hung_By_Son_full_offline_v3_20261002.jar`. Verification record/manifest: `build/full-offline-20261002/release-verification.txt`. Host, trade and service-audit logs are in that same directory.
- Scope: audit found ten Brain instruction keys used by only the enhancement and V3 trade modules, plus seven activation entitlement flags. The getter now returns local results directly, unknown keys have no remote fallback, secret-request workers are removed from the getter path and made inert, activation worker is inert, and local startup instructions are supplied. Existing trade formulas and controllers are preserved. New enhancement instructions use the client's own native opcode-67 sender and its packet schema.
- User-specified **Thông minh** policy: only current +8/+9/+10 use cỏ 4 lá; all other levels use none. Manual clover modes remain available. Clover template IDs are resolved from the live bag. Missing clovers stop; unwanted previously selected clovers are deselected. Enhancement requests use gold, are at least 500 ms apart, and wait for the native result/animation. The response hook covers native result types 2 and 5; a missing result for 12 seconds stops rather than resubmitting.
- Verification: 999 entries/221 classes; exactly four changed classes (secret service, activation, enhancement controller, native message receiver) and one added class `HungBySonOfflineLocal`. Manifest/resources and prior class versions are identical; added support is major 48 with no StringBuilder/invokedynamic. ZIP CRC and entry comparison pass. JVM `-Xverify:all` reflection linking of all 221 original release classes passes. The real-class host fixture passes 93 checks, including actual packet payloads captured in memory, real numeric getters, rights/guide, native response parser, pacing, timeout and missing-clover cases. Auto Đi Buôn V3 regression passes.
- MOD connector and worker audit passes. The two remaining native Connector call sites are game server-list HTTP and game socket; the native HTTP helper's callers are server-list screens. Bạch Hổ and the previous features remain present. No credentials or live account were used, and no emulator gameplay or real-server test was performed. Exact live item names, server acceptance, actual travel and game rendering remain unverified. The legacy Luna controller/bridge suite is not applicable to this independent binary; unchanged controller/resource bytes were checked instead.
- Vietnamese inventory and instructions: [HUNG_BY_SON_OFFLINE.md](HUNG_BY_SON_OFFLINE.md).

## Hung_By_Son enhancement wait and background-animation repair (2026-10-02)

- The first wait/animation repair was SHA-256 `F4479FB8DC6B0E2098E3489B8E5F96E7171D4B65C1D111E86CE5C6C6F97CF9D4`. Input: first local-services version, SHA-256 `8299D3CBA6B96E8605D745A6BCAAC5F3A4F9A0AD6DF914AA1D7A3412373BBAF6`. Backup: `../jar/backup/Hung_By_Son-before-enhancement-wait-fix-20261002.jar`. Build: `build/releases/Hung_By_Son_enhancement_wait_fix_20261002.jar`; record/manifest and logs: `build/enhancement-wait-fix-20261002/`.
- User reported a blank-reason “missing enhancement instructions” stop 15–30 seconds after reaching the NPC; initially the default setting had enhanced one item to +1. The old controller counted every false action return (including normal acknowledgement/pacing waits) towards 150 failed secret requests. The fixture reproduces the blank-reason stop through its actual update loop. A separate issue held the native result animation at stage 1 when auto ran in the background: only the visible enhancement tab's update could finish that stage.
- The controller's old retry method now delegates to local wait handling with explicit reasons and a 12-second readiness timeout. The local helper retains the NPC map/position gate and returns the controller to its existing navigation phase if the player leaves that position. Real game results are still required. If the enhancement tab is not visible, background animation state is released 500 ms after a real native result; visible native-tab animation remains unchanged. Existing gold payment, 500 ms pacing and the user rule (four-leaf only at current +8..+10) remain in force.
- Only the enhancement controller and `HungBySonOfflineLocal` change; all 999 entry names, 221 classes, prior class versions, resources and manifest are preserved. CRC/entry comparison, full offline audit, all 221 class links, the 93-check host suite, 13 new wait/full-controller-cycle checks and V3 trade regression pass. The new regression verifies continuing to the next selection after a native enhancement result and preserves visible-tab animation. Source: `HungBySonFullOfflinePatcher repair` and the maintained local template; regression: `fixtures/hung-by-son/HungBySonEnhancementWaitRegressionTest.java`.
- Last verified: 2026-10-02. No live-server gameplay or emulator rendering test was performed for this repair. The reproduced client-side error is fixed; any actual server refusal or missing selection acknowledgement remains subject to real-server testing and now has a specific wait reason.

## Hung_By_Son native enhancement NPC-session setup (2026-10-02)

- The NPC-session repair version was SHA-256 `FB8A69FED2179E55836994820760D8C3903CA561A72F4EC0AE3110B9F84CB51E`. Input: first wait repair above, SHA-256 `F4479FB8DC6B0E2098E3489B8E5F96E7171D4B65C1D111E86CE5C6C6F97CF9D4`. Backup: `../jar/backup/Hung_By_Son-before-enhancement-npc-session-20261002.jar`. Build: `build/releases/Hung_By_Son_enhancement_npc_session_v2_20261002.jar`; record/manifest and logs: `build/enhancement-game-response-20261002/`.
- User screenshot showed the 12-second game-result timeout after the first repair. Static tracing confirms opcode 67 reaches the hooked native response parser and gold request `(2,0,0)` matches the manual sender. The auto path had omitted opening the NPC's enhancement session. It also ignored native notice subtype 3 and generic game notice opcode 37 while waiting, which could hide a game rejection behind the timeout. Without a live packet trace, the exact server-side cause of that screenshot is not established.
- Setup now follows native interaction: synchronize position (4), request nearby NPC menu/data (23/5), capture the equipment-enhancement row by its actual label and NPC ID, and press it through the native dispatcher (-30). Equipment is not selected until the native enhancement tab (mode 0) confirms session setup. The selected item is cleared before selection to require a fresh server acknowledgement. Menu setup is bounded and requests are not flooded.
- Native opcode-67 subtype-3 text aborts the auto attempt with its actual message. Game notice 37's body is retained while a request is outstanding. A result timeout can now distinguish a missing response from a received-but-incomplete enhancement response and can include the game notice. Real result/acknowledgement gating, no automatic resubmission after timeout, gold payment, 500 ms pacing and four-leaf-only-at-current-+8..+10 remain in force.
- Exactly three classes change (local support, MOD menu dispatcher and native receiver). All 999 entries/221 classes, previous class versions, resources and manifest are preserved; ZIP CRC/entry comparison and offline-call audit pass. JVM `-Xverify:all` all-class linkage passes. Full offline fixture: 94 checks; wait/controller regression: 13 checks; NPC-session/rejection fixture: 17 checks; total 124 plus V3 trade regression. Tests use original release bytes with only the outgoing packet queue replaced by a memory sink; native NPC menu dispatcher and rejection/notice parsers are exercised, with a simulated native-tab acknowledgement.
- Last verified: 2026-10-02. No live-server or emulator-rendering validation has been done for this new setup. The next real-game run is needed to confirm NPC labels, session acceptance, materials/currency requirements and the full response sequence. A remaining refusal should now expose the game message instead of a blank MOD-instruction error or a context-free timeout.

## Bạch Hổ variant from Luna, endpoint/name only (2026-10-02)

- Published: `../jar/bachho_final.jar`, SHA-256 `644BB4348E420C933213C51F4F2C0A304A2AD867C6E8C5EC0F50C6DE674C5C46`. Source: `../jar/luna_final.jar`, SHA-256 `DA57FDC4A469A1D52AEEB073C9E2A444E5CFBE94B9A32C511CFB0D5EA11B4081`. The user typed `luna_fiinal.jar`; the existing source is `luna_final.jar`. Source bytes are preserved. This is a new file, so no previous Bạch Hổ artifact needed backup.
- Scope explicitly limited to endpoint and server label: **Bạch Hổ / 103.77.166.204**, with the original port 19129. This IP was obtained earlier for `hs8.teamobi.com`, the Bạch Hổ hostname in `HungSon-update.jar`. The five plain endpoint constants, one XOR-5 endpoint constant and one server-name constant are replaced. No feature code is rebuilt or transferred from Hung_By_Son.
- Tool: `tools/luna/ServerVariantPatcher.java`, with `--keep-manifest`. Candidate: `build/releases/bachho_final_candidate_20261002.jar`; verification record and regression logs: `build/bachho-variant-20261002/`. The new option leaves all manifest bytes unchanged (including MIDlet name HSOLUNA, vendor, version and RMS identity); the tool's prior naming mode remains available.
- Last verified: 2026-10-02. 946 entries/216 classes/730 resources preserved. Exactly three endpoint/name configuration classes differ; changes are confined to constant-pool string values, with all method bytes and other classfile bytes preserved. All other entries, including manifest and resources, are byte-identical, and every class version is unchanged. ZIP CRC, expected endpoint/name loads, resource comparison and JVM `-Xverify:all` class loading pass. Controller host, route host and legacy route regressions pass.
- The current semantic fixture cannot compile against this source artifact because its `NoticeReader` has no `loginCooldownDelayMillis(String)` method; the fixture targets a newer semantic source API. This same API mismatch exists in the unmodified Luna source JAR. No feature/API change was introduced to accommodate it, in accordance with the endpoint/name-only request. No emulator rendering or live-server connection test was performed.

## Bạch Hổ cast delay +100ms (2026-10-02)

- Verified new artifact: `build/bachho-cast-20261002/bachho_final.jar`, SHA-256 `C211A72A69A02D081A1CFBCDF731F897648FAB9EE0B0C9B73CCA0AA54802C1A2`. Input: the endpoint/name-only version above, SHA-256 `644BB4348E420C933213C51F4F2C0A304A2AD867C6E8C5EC0F50C6DE674C5C46`. Rollback: `inputs/releases/bachho_final-before-cast-plus100-20261002.jar`. The user typed `backho_final.jar`; the existing Bạch Hổ release is `bachho_final.jar`. Replacement of `../jar/bachho_final.jar` is pending: Windows reports it is in use by another process, and the release still has its input hash. The user was asked to close the game/emulator or take the separate verified artifact.
- Scope: auto cast gate 100ms -> 200ms; native request comparison now uses the original skill interval +100ms. The comparison remains strict `>`. Native interval metadata, separate per-skill cooldown, animation, mana checks and packet senders remain unchanged. Actual gameplay cadence may still be constrained by those native gates. Bạch Hổ name/IP `103.77.166.204`, port 19129, manifest and every other endpoint JAR are unchanged.
- Maintained sources: `tools/luna/NativeCombatDelayPatcher.java` and its read-only discovery helper `CombatCadencePatcher.java`. Exact `owner + member + descriptor` symbols and input/output hashes are generated into `symbols/bachho-combat-20261002.properties`; lookup rejects missing or ambiguous gates/writers. Candidate: `build/releases/bachho_final_cast_plus100_20261002.jar`. Verification records/logs: `build/bachho-cast-20261002/`.
- Binary verification passes: 946 entries, 216 classes, 730 resources, one façade, 23 semantic classes, legacy bridge present; 186 major-47 and 30 major-51 classes. Exactly two classes change. Inverting the one initializer constant and one comparison addition yields bytecode/member structures identical to input. Other entries are byte-identical, including manifest, resources and endpoint configuration. ZIP CRC, original symbol catalog, resource comparison and JVM `-Xverify:all` all-class loading pass.
- Binary-boundary fixture `fixtures/binary/LunaCombatCadenceHostTest.java`: 66 checks pass against input and candidate, including actual initialized auto delay, real auto method with a deterministic clock, and request comparisons extracted from the native method at exact boundaries and large intervals. Controller, route and legacy route regressions pass. The existing semantic fixture API mismatch described above persists; unchanged semantic classes were not rebuilt to accommodate it.
- MicroEmulator Headless startup was observed for 12 seconds with memory RMS and isolated configuration under `build/bachho-cast-20261002/emulator-home/`; no startup exception/error was recorded. This is startup verification only. No in-game combat, rendered gameplay or real-server accepted-request timing has been tested. Behavior record: [LUNA_BEHAVIOR.md](LUNA_BEHAVIOR.md).

## Bạch Hổ another +100ms cast delay, total +200ms (2026-10-02)

- Verified new artifact: `build/bachho-cast-plus200-20261002/bachho_final.jar`, SHA-256 `898E12A2FA80960F5F664DAE19578021D443566504086075E0B0206D1BFE6CCC`. Candidate: `build/releases/bachho_final_cast_plus200_20261002.jar`. At the start of this request, `../jar/bachho_final.jar` matched the preceding +100ms artifact (`C211A72A69A02D081A1CFBCDF731F897648FAB9EE0B0C9B73CCA0AA54802C1A2`), indicating it had been copied there since the previous locked replacement attempt. That version is backed up at `inputs/releases/bachho_final-before-cast-plus200-20261002.jar`.
- Scope: auto gate 200ms -> 300ms; native request interval +100ms -> +200ms relative to the endpoint-only baseline. The strict `>` comparisons, original interval metadata, per-skill cooldowns, animation, mana checks, packet senders, Bạch Hổ endpoint/name, manifest and all other features are preserved. The candidate was rebuilt from the verified original endpoint-only backup with a cumulative 200ms increment using `NativeCombatDelayPatcher`; no patch is applied twice to an already modified comparison.
- Exact symbol/patch record: `symbols/bachho-combat-plus200-20261002.properties`; input hash remains `644BB4348E420C933213C51F4F2C0A304A2AD867C6E8C5EC0F50C6DE674C5C46`. Verification records: `build/bachho-cast-plus200-20261002/`. The boundary fixture now accepts the expected cumulative increment and checks the exact auto threshold for that variant.
- Verification passes: 67 binary-boundary checks, ZIP CRC, structural inversion proving only the intended two classes change, 946 entries/216 classes/730 resources, unchanged manifest/resources and class versions, original symbol catalog, one hook façade/23 semantic classes/legacy bridge, JVM `-Xverify:all` class loading, controller/route tests and legacy route regression. The existing semantic-fixture API mismatch remains documented above; semantic classes were not rebuilt. Headless MicroEmulator startup was observed for 12 seconds with memory RMS and isolated configuration, with no startup errors. Live combat, rendering and server acceptance/timing remain untested.
- The initial replacement attempt failed because Windows reported the file was in use; that attempt retained hash `C211A72A69A02D081A1CFBCDF731F897648FAB9EE0B0C9B73CCA0AA54802C1A2`. Failed-attempt record: `build/bachho-cast-plus200-20261002/publish-result.json`. No game/emulator process belonging to the user was stopped.
- Published after the user closed the game/emulator and requested replacement: current `../jar/bachho_final.jar` SHA-256 `898E12A2FA80960F5F664DAE19578021D443566504086075E0B0206D1BFE6CCC`, 1,709,610 bytes. Atomic replacement and post-copy hash verification passed; the published bytes match the tested artifact exactly. The existing backup was verified and preserved. Completion record: `build/bachho-cast-plus200-20261002/publish-completed-20261002.json`. Auto gate is 300ms and the cumulative added request interval is 200ms.

## Bạch Hổ third +100ms cast delay, total +300ms (2026-10-02)

- Published `../jar/bachho_final.jar`: SHA-256 `43103DC50F7BD130C88F094FD966BAFABC0C628D0581C0E1652A2B7A2782897A`, 1,709,609 bytes. Previous release hash `898E12A2FA80960F5F664DAE19578021D443566504086075E0B0206D1BFE6CCC` was verified and backed up at `inputs/releases/bachho_final-before-cast-plus300-20261002.jar`. Atomic replacement succeeded; the published hash matches the tested candidate exactly.
- Scope: auto gate 300ms -> 400ms; native request interval +200ms -> +300ms relative to the endpoint-only baseline. The strict `>` comparisons, native interval metadata, separate per-skill cooldowns, animation, mana checks, packet senders, Bạch Hổ name/IP/port and all other features remain unchanged. `NativeCombatDelayPatcher` rebuilt from the verified endpoint-only backup with cumulative increment 300ms; the prior patched release was not patched twice.
- Candidate `build/releases/bachho_final_cast_plus300_20261002.jar`; same-name artifact `build/bachho-cast-plus300-20261002/bachho_final.jar`; exact symbols and hashes `symbols/bachho-combat-plus300-20261002.properties`. Verification and publication records are in `build/bachho-cast-plus300-20261002/`, including `publish-result.json`.
- Passed: 67 binary-boundary checks covering the 400ms auto threshold and native interval +300ms, ZIP CRC, structural inversion proving only the intended two classes change, 946 entries/216 classes/730 resources, unchanged manifest/resources/class versions, original symbol catalog, hook/bridge checks, JVM `-Xverify:all` class loading, controller/route and legacy route regressions. The previously recorded semantic-fixture API mismatch persists; unchanged semantic classes were not rebuilt.
- Headless MicroEmulator startup was observed for 12 seconds with memory RMS and isolated configuration; no startup errors were recorded. No live combat, rendering or server-accepted request timing has been tested for this variant. All other endpoint releases are preserved.

## Hung_By_Son original combat restored (2026-10-02)

- Published `../jar/Hung_By_Son.jar`: SHA-256 `EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC`. Input was the current NPC-session repair release, SHA-256 `FB8A69FED2179E55836994820760D8C3903CA561A72F4EC0AE3110B9F84CB51E`, backed up at `../jar/backup/Hung_By_Son-before-restore-native-combat-20261002.jar`. Candidate: `build/releases/Hung_By_Son_native_combat_20261002.jar`; verification and publication records: `build/combat-restore-20261002/`.
- User requested removal of the 300 ms combat change and restoration of the original attacks against monsters and players. `tools/legacy/hsosky/HungBySonRestoreCombatPatcher.java` identifies exactly the two classes changed by the historical cast-300 patch using pinned pre-patch and post-patch JARs, checks that their current bytes still match the cast-300 reference, then restores the original class bytes. Both restored classes also match the original unmodified `Hung_By_Son` JAR byte-for-byte. This removes the forced frame-1 emission, frame-2 early recovery, and fixed 300 ms request/auto gates together; the native auto gate is 600 ms and native interval/animation logic is restored.
- Verification passed: 999 entries/221 classes/731 non-directory non-class entries; ZIP CRC, identical class versions, exactly two restored classes, and every other entry byte-identical to the input. JVM `-Xverify:all` reflection linking of all 221 classes passed without initialization or network access. Atomic replacement and published hash comparison passed. Default 30 FPS, 12 skill cells, the 500 ms enhancement intro, Bạch Hổ server row, full local MOD services, and all subsequent enhancement/session fixes remain in the current input's bytes.
- No live combat or emulator rendering was performed. Other endpoint JARs were not edited. The historical `HungBySonCast300Patcher` remains available as a prior experiment and is not applied to this release.

## Bạch Hổ phantom white horse fix (2026-10-03)

- Last verified: 2026-10-03. Published `../jar/bachho_final.jar`, SHA-256 `5C2244ED034817A1CBF7EA89054AAEBEC1D2A1A7C472AB29936864F0F03783A9`, 1,709,602 bytes. Input was the preceding +300ms cast release, SHA-256 `43103DC50F7BD130C88F094FD966BAFABC0C628D0581C0E1652A2B7A2782897A`; verified rollback copy: `inputs/releases/bachho_final-before-horse-fix-20261003.jar`. Atomic replacement and post-publication hash comparison passed; all other endpoint JARs and the golden baseline retain their hashes.
- Root cause proved in the current binary: `HSOSKYPetFeedSupport.reengageAutoAttack()V` wrote mount byte `1` to the local player independently of Auto Horse or a server mount update. The native renderer and movement bonus consume that byte. Auto Attack recovery after movement, mode changes, world entry, and AutoTrade/N4 combat restoration all use this method, so they could create a local horse/speed mismatch while actually unmounted.
- Maintained patcher: `tools/luna/NativeHorseStatePatcher.java`. It discovers the exact mount field from the native speed accessor, checks inheritance/current-player symbols and the single MOD writer, and removes only `GETSTATIC currentPlayer; ICONST_1; PUTFIELD mount:B`. It does not rebuild the stale legacy PetFeed source. Catalog: `symbols/bachho-horse-20261003.properties`; read-only cross-reference: `tools/luna/NativeHorseStateAnalyzer.java`.
- Candidate: `build/releases/bachho_final_horse_fix_20261003.jar`; same-name verified copy: `build/bachho-horse-20261003/bachho_final.jar`. Evidence, manifest and publication record: `build/bachho-horse-20261003/`, including `patch-verification.txt`, `release-verification.txt`, `horse-fixture.txt` and `publish-result.json`.
- Scope verified: 946 entries/216 classes/730 resources, one modified support class, three executable instructions removed, class major versions retained (maximum 51), one hook façade/23 semantic classes/legacy bridge. Reconstruction of the full support class proves all other executable code, frames and exception handlers are unchanged; only line-label metadata is excluded from reconstruction comparison because the deleted source line coalesces with the try-end label. Every other JAR entry, including manifest, native server mount handlers, speed tables, horse lifecycle, endpoint and combat gates, is byte-identical. Native mount writer count changes from seven to six; the support mount writer changes from one to zero.
- `fixtures/binary/LunaHorseStateHostTest.java` passed 142 checks using the preserved input and candidate. It executes the actual before/after recovery bytecode in an isolated harness: the original forces mount `1`, while the fix preserves unmounted `-1` and existing mount states over repeated calls. Missing player and HP `-1`/`0` remain no-ops; all other captured combat state matches. The native bonus accessor is executed with a stub byte table for `-1`/`0`/`1`/`2`; native mount writer sites and ZIP CRC also pass. The harness does not log in or send game packets.
- Other checks passed: all 133 original symbols; JVM `-Xverify:all` loading of 216 classes; resource comparison; semantic/controller/route fixtures compiled from current maintained source; legacy route regression; 67 combat boundary checks confirming auto gate 400ms and native interval +300ms. Semantic classes in the JAR were not rebuilt. Headless MicroEmulator observed for 12 seconds with isolated home and memory RMS; no startup error and empty stderr. Live riding, rendered gameplay, movement/server acceptance and in-game combat remain untested.

## Bạch Hổ auto diệt ma (2026-10-03)

- Last verified: 2026-10-03. Published: `../jar/bachho_final.jar`; tested candidate: `build/releases/bachho_final_ghost_auto_20261003.jar`; same-name copy: `build/bachho-ghost-20261003/bachho_final.jar`. SHA-256 `A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E`, 1,712,850 bytes. Input SHA-256 `5C2244ED034817A1CBF7EA89054AAEBEC1D2A1A7C472AB29936864F0F03783A9`; verified rollback: `inputs/releases/bachho_final-before-ghost-auto-20261003.jar`. The first replacement was blocked by the user's running emulator. At the user's explicit request, Windows Restart Manager identified `javaw` PID 18912 (`ZangVPS-Micro`) as the file owner; its window closed gracefully. Atomic replacement then passed, with the published hash matching the tested candidate. Publication/close records: `build/bachho-ghost-20261003/{publish-result.json,lock-owners.json,close-emulator.json}`.
- Reuses the maintained GhostMod OCR/answer port `tools/legacy/hsosky/HungBySonGhostSupport.java` and structural `HungBySonGhostPatcher.java`, compiled with UTF-8 / `--release 7` against external MicroEmulator/ASM dependencies. The network image-store branch for id 9999 calls the new support automatically; no menu entry is added. Native sender opcode -28 and captcha-holder bridge are discovered from this exact input. Symbols/input-output hashes: `symbols/bachho-ghost-20261003.properties`; patch report: `build/bachho-ghost-20261003/patch-verification.txt`.
- Archive comparison proves 947 entries / 217 classes / 730 resources: only the network handler changes, and `HungBySonGhostSupport.class` is added (major 48). All other 945 entries, including manifest, endpoint, horse fix, native combat gates, semantic controllers and legacy bridge, remain byte-identical. Maximum major remains 51. Native bytecode explicitly clears the captcha holder to null; the field, sender getter and answer method are public. Read-only evidence: `captcha-field-references.txt`, `bridge-access.txt`, `native-answer-sender.txt` under the verification directory.
- Passed: published-candidate OCR/controller fixture (170 checks with synthetic opaque/transparent captcha images, deterministic clock and simulated native bridges), all 133 original symbols, JVM `-Xverify:all` linking of 217 classes, unchanged resources, current semantic/controller/route tests and legacy route regression. Headless MicroEmulator observed for 12 seconds using isolated home and memory RMS, with empty stderr and no startup errors. Manifest and all logs are in `build/bachho-ghost-20261003/`.
- Not verified: real Bạch Hổ captcha images, gameplay/focus timing, actual answers accepted by the server, or rendered in-game ghost interaction. Native holder-null is an observed clear, not proof of ghost death. The port uses a fixed 1.5-second startup delay and a ten-sequence limit; legacy GhostMod instead waits for focus state and retries indefinitely. The native answer sender does not itself check focus. Other endpoint JARs and the immutable golden baseline retain their pre-work hashes.

## Bạch Hổ: Noclip OFF, nguyên liệu trắng, tọa độ tự đánh (2026-10-03)

**Đã rollback ngày 2026-10-04 theo yêu cầu người dùng.** Các kiểm tra dưới đây là lịch sử của bản bị loại, không xác nhận bản này đạt khi chơi thực tế.

- Published `../jar/bachho_final.jar`, SHA-256 `2BE9C4DB203EBAD94DB20B21D1F3DD0F4B611C90756D738DF44272BF2C2CF840`, 1,725,478 bytes. Tested candidate: `build/releases/bachho_final_three_upgrades_v3_20261003.jar`. Input was the preceding ghost-auto release (`A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E`); rollback: `inputs/releases/bachho_final-before-three-upgrades-20261003.jar`. Atomic replacement/hash verification passed. Other endpoints, donor and golden retain their input hashes.
- Features: Noclip defaults OFF and resets once at a detected native login/disconnect boundary; session choices survive map/zone changes and N4 temporary requests. A world-entry callback schedules material closure and saved-position combat. The new **Auto tự đánh >** menu has ON/OFF, save position and clear position. Coordinates persist in `bachho_attack_pos` RMS; native auto intent/mode/RMS remain in use. No saved position passes native combat through. With a saved position, the controller returns to its map/zone and anchors the donor's stationary spot with a 30px Manhattan leash. Native recovery yields to this controller; native reengagement is gated only while returning. See [behavior](LUNA_BEHAVIOR.md).
- Donor audit: current `Hung_By_Son.jar` SHA-256 `EA401D771635723AD2EE2BF3EB5FE9A7D5F490559DE6F97983BD1F107A5659AC` has the negative material-toggle helper, but zero direct callers for that helper. Its active login trigger could not be established. Root-menu/state-check semantics come from the separate maintained `tools/legacy/ghostmod/GhostMod.java`; the Bạch Hổ login trigger is newly implemented. Audit: `build/bachho-drop-audit-20261003/symbols-report.txt`.
- Native discovery proved zero-based zone storage and sender opcode **51**, via native menu case 13 and inbound parser. This differs from opcode 54 in the older `PROTOCOL.md`. Exact catalog/evidence: `symbols/bachho-combat-position-20261003.properties`, `build/bachho-position-symbols-20261003/evidence.txt`.
- Maintained build: `tools/luna/build-bachho-upgrades.ps1`, `BachHoNoclipDefaultPatcher.java`, `BachHoAutomationPatcher.java`, `BachHoAutomationBridge.java.in`, semantic source and fixtures. Final catalogs: `symbols/bachho-noclip-v2-20261003.properties`, `symbols/bachho-automation-v3-20261003.properties`. Pass the exact rollback input plus external dependency directory to the build script; it stages an output without publishing other endpoints. A complete rebuild produced identical bytes in all 953 entries (ZIP timestamps differ).
- Passed: 5 semantic/controller/route/material/combat suites; 61 Noclip lifecycle checks; 755 candidate hook/RMS/anchor/native-body checks; all 133 baseline symbols; initialized legacy route regression; 223-class JVM linking; unchanged 730 resources/manifest; maximum class major 51. Native rearm retains its complete former body behind a three-instruction guard, including the horse fix. Combat cadence (67 checks) and ghost OCR/controller (170 checks) passed on v2; those classes are byte-identical in v3. Headless MicroEmulator observed v3 for 12s with isolated home/memory RMS: all new classes loaded, empty stderr, no startup error. Evidence/publication record: `build/bachho-features-20261003/`.
- Failed intermediates were never published. Initialized class/emulator verification found a moved stackmap frame in the first Noclip intermediate; the maintained patcher now inserts the reset after the entry frame. Not verified with a Bạch Hổ account: login/reconnect, server material labels/toggle acceptance, rendered menus, saved map/zone return, movement acceptance or combat.

## Rollback ba nâng cấp Bạch Hổ (2026-10-04)

- Người dùng báo bản mới không đạt và yêu cầu rollback. `../jar/bachho_final.jar` được khôi phục byte-for-byte từ `inputs/releases/bachho_final-before-three-upgrades-20261003.jar`: SHA-256 `A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E`, 1,712,850 bytes.
- Bản bị loại SHA-256 `2BE9C4DB203EBAD94DB20B21D1F3DD0F4B611C90756D738DF44272BF2C2CF840` được giữ tại `inputs/releases/bachho_final-rejected-three-upgrades-20261004.jar`. Atomic replacement, hash bản khôi phục/hash bản lưu và hash các endpoint khác/donor/golden đều đạt. Bằng chứng: `build/bachho-rollback-three-upgrades-20261004/rollback-result.json`.
- Source/template mặc định Noclip và pipeline Luna chuẩn được trả về trạng thái trước nâng cấp; bỏ các hook reset/login mới khỏi template chuẩn. Mã thử nghiệm và fixture riêng được giữ để điều tra, không có trong JAR đã khôi phục. Snapshot source bị loại nằm dưới thư mục audit `source-rejected/`. Pipeline `build-bachho-upgrades.ps1` là thử nghiệm đã bị loại, không phải đường phát hành hiện hành.

