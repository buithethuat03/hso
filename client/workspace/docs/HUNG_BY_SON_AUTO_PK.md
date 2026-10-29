# Hung By Son — Auto PK và vòng chọn mục tiêu

Nâng cấp bản đơn ngày 2026-10-25. Yêu cầu gốc và nhật ký bàn giao:
[HUNG_BY_SON_AUTO_PK_UPGRADE_PLAN.md](HUNG_BY_SON_AUTO_PK_UPGRADE_PLAN.md).
Hash và trạng thái phát hành xem [RELEASES.md](RELEASES.md).

## Cách dùng

Mở **Menu → Auto đánh >**. Chọn một trong hai chế độ:

| Chế độ | Mục tiêu của vòng đặc biệt | Phạm vi / thứ tự |
|---|---|---|
| Auto chỉ quái | Tối đa 20 quái sống hợp lệ | Toàn map đang tải, gần trước như bản trước |
| Auto PK | Tối đa 20 người có thể PK theo điều kiện native | Bán kính 365px, gần trước |

**Auto PK thay Auto quái + PK và không chọn quái.** Mục **Tấn công quái thường** đã bỏ khỏi menu; cơ chế vòng đánh đặc biệt mới là mặc định. Callback cũ -117 chỉ còn giữ cho tương thích ABI/fixture lịch sử, không có dòng menu để đổi cài đặt này. Bật/tắt Mê cung không làm ngầm tắt cơ chế mặc định đó.

**Tấn công đặc biệt BẬT** sử dụng cùng vòng 28ms, khóa mặc định 575ms (từ 2026-10-29 mặc định Auto, xem [ModUpLv port](HUNG_BY_SON_MODUPLV_PORT.md); Auto PK không đổi mục tiêu), bộ chiêu đã học, ưu tiên Trọng kiếm và retry một lần của bản trước. Như người dùng yêu cầu, nó vẫn tự đánh khi **Auto tự đánh TẮT**. Muốn dừng vòng độc lập này phải tắt Tấn công đặc biệt. Auto tự đánh và Farm vẫn chuyển quyền theo cơ chế cũ.

Tấn công đặc biệt TẮT dùng nhịp/cast native hiện có; khi Auto PK được chọn, đường thường này cũng chỉ chọn người hợp lệ trong 365px. Nó giữ một mục tiêu mỗi lượt native, số mục tiêu phụ theo chiêu native; giới hạn 20 người là hành vi của vòng đặc biệt. Mục tiêu PK thường được xếp lại khi sẵn sàng chọn chiêu tiếp theo, tránh hủy cast hợp lệ đang bận chỉ vì có người khác gần hơn.

Khi Farm đang bật, vòng đặc biệt tiếp tục đánh quái theo cơ chế Farm, kể cả mode Auto PK được lưu từ trước. Phó bản đứng chốt vẫn chọn chế độ quái; Mê cung vẫn độc quyền. Buff giữ mục tiêu bản thân/đồng đội và opcode 40.

## Vòng xanh 365px và đường kẻ mục tiêu

Auto PK có vòng tròn xanh tâm theo nhân vật, bán kính **365 pixel của bản đồ** (500 → 400 → 365 theo các yêu cầu ngày 26/10, áp dụng cả vòng và bộ lọc thực tế). Vòng hiện **ngay khi Auto PK được chọn**, kể cả Auto tự đánh và Tấn công đặc biệt cùng TẮT, khi world hợp lệ và Farm không giữ quyền. Đây là xem trước vùng chọn, không tự bật đánh. Auto chỉ quái không hiện vòng này.

Nhãn menu đã sửa cho cả hai trạng thái: **Auto PK** / **Auto PK [x]**. Bản trước còn sót
`Auto quái + PK [x]` ở nhánh đang chọn dù bộ lọc đã là PK-only.

Vòng dùng cùng tọa độ world với bộ lọc PK, theo camera và scale native. Biên 365 được tính vào vùng; 366 bị loại. Đây là hình tròn theo khoảng cách Euclid, không phải ô vuông. Không dùng hàm khoảng cách native có thể tràn số để giới hạn bán kính.

**Sửa lệch tâm 26/10:** callback vẽ nằm sau lớp vẽ entity/focus của world và ngay trước lệnh
reset graphics về screen, chỉ một lần mỗi frame. Hai vị trí cũ là dịch minimap sau reset,
không phải điểm reset camera. Vòng hiện dùng cả camera và độ dịch rung bu/bv đang áp cho actor.

Đường kính là 730px; trên màn hình nhỏ, đường viền có thể nằm hoàn toàn ngoài viewport. Không thu nhỏ vòng để vừa màn hình. Vùng vẽ bị cắt theo clip native; không đổi camera, màu hoặc clip của phần vẽ tiếp theo.

**Đường kẻ mục tiêu:** cùng lúc với vòng, painter kẻ đường từ chân nhân vật tới chân mỗi người
mà bộ chọn PK có thể đánh: cùng bộ lọc native, FIFO 10 tên chiến trường và căn vị trí server trước
bước bán kính như `nearestPlayers`. Khi bật Mode chiến trường và có Xà nữ trong vòng, **chỉ** kẻ đường **đỏ** (`0xFF0000`) tới Xà nữ. Không có Xà nữ: level 40 có đường **xanh da trời**
(`0x00BFFF`, đúng nhóm được ưu tiên); mọi trường hợp khác **trắng**. Kẻ tới tất cả người hợp lệ trong
vòng, kể cả khi hơn 20 người (đòn đặc biệt vẫn chỉ gửi 20 người xếp đầu). Vòng vẽ sau, đè lên đường;
màu graphics được trả lại sau khi vẽ.

Từ 2026-10-28, khi bật Mode chiến trường, đường tới người chơi đang focus là
**cam** (`0xFFA500`), ưu tiên hơn màu xanh của level 40. Nhánh đánh thường/focus
tay dùng focus native; Auto với vòng Tấn công đặc biệt dùng mục tiêu khóa thực
của vòng đó. **Xà nữ luôn giữ màu đỏ**, kể cả khi đang focus. Tắt chiến trường
thì mọi đường người chơi trở lại trắng.

## Mode chiến trường

Khi bật Mode chiến trường + Auto PK:

0. **Xà nữ** (boss quái của chiến trường) còn sống trong 365px: chỉ đánh Xà nữ, bỏ qua mọi người.
   Vòng đặc biệt gửi tối đa 20 Xà nữ (HP thấp trước) bằng opcode 9; đường thường xếp Xà nữ trên level 40.
   Tên so khớp `"Xà nữ"` bỏ qua hoa/thường và khoảng trắng hai đầu (dạng không dấu không khớp).
   Xà nữ ngoài vòng hoặc đã chết: tiếp tục các bước dưới. Tắt chiến trường thì không nhắm Xà nữ.
1. Lọc mục tiêu PK hợp lệ và trong bán kính 365px trước.
2. Người **level đúng 40** đứng trước.
3. Người level khác đứng sau; lấy tối đa 20.

Trong cùng nhóm (Xà nữ, level 40, còn lại) **HP hiện tại thấp đánh trước** (từ 2026-10-27, trước đó gần trước);
HP bằng nhau thì gần trước. Hơn 20 mục tiêu thì giữ 20 mục tiêu HP thấp nhất. Đường thường (Đặc biệt TẮT) dùng
`priorityRank` = nhóm ở bit 28–29 + HP đảo ở 28 bit thấp, cùng thứ tự đó.

**Khóa mục tiêu** (từ 2026-10-27): mục tiêu đã chọn được giữ đầu danh sách (Đặc biệt BẬT, field `pkLock`) hoặc làm
mục tiêu của Auto đánh (Đặc biệt TẮT, mục tiêu hiện tại xếp như HP 0) cho tới khi nó chết, ra khỏi 365px hoặc có
nhóm cao hơn trong vòng; khi đó chọn lại HP thấp nhất của nhóm cao nhất. Người khác tụt máu thấp hơn hay người quay
lại vòng không giành khóa. Tắt chiến trường không khóa.

Không dùng level để vượt bộ lọc PK hoặc phạm vi. Người chết, bản thân, đối tượng bị xóa, người không thể PK và tên bị danh sách chiến trường loại vẫn không được chọn. Tắt chiến trường quay về gần trước, không phân biệt level hay HP. Cơ chế FIFO 10 tên và ngoại lệ không ghi level 40 vào FIFO giữ nguyên.

Vị trí server của actor được đồng bộ qua bridge chiến trường trước bước tính phạm vi. Trường hợp tọa độ cũ ngoài 365 nhưng tọa độ server mới bên trong (và ngược lại) được kiểm riêng.

## Gửi đòn và hiệu suất

- Quái dùng serializer native opcode 9; người dùng opcode 6; buff dùng opcode 40.
- Danh sách quái/người không trộn loại. Mỗi lượt đặt cooldown, trừ MP và đếm thống kê một lần.
- Frame giữ cùng khóa với thao tác chọn mode từ lúc lấy mode đến enqueue, để lệnh Quái ↔ PK không chen vào giữa chọn danh sách và gửi gói.
- Nhánh chọn quái toàn map và dữ liệu canh nhịp/thống kê của JAR gốc được giữ nguyên; PK dùng mảng xếp hạng cố định 20 phần tử, không cấp phát mảng theo số người mỗi frame. Danh sách PK chỉ quét một lần cho các chiêu tấn công trong cùng frame.
- Bộ lọc chỉ thấy actor client đã tải, không tự yêu cầu vị trí người ngoài dữ liệu đang có.

Kiểm chứng phía máy gồm policy, bytecode/candidate, payload tại queue, race chuyển mode, hồi quy native và ảnh giả lập. Đối chiếu nhịp với JAR nguồn dùng đồng hồ/cooldown mô phỏng; đó không phải phép đo trúng/phút trên server thật.

Chưa xác minh trên server thật rằng một đòn PK được nhận đủ 20 người, tầm sát thương hoặc khóa PK giống khóa quái. Giới hạn danh sách và bộ canh nhịp phía client không thay luật server.

## Nguồn và kiểm thử

- `src/luna/features/AutoPkTargetPolicy.java`: bán kính và xếp hạng top 20, dùng lại mảng.
- `src/luna/api/AutoPkPriorityAccess.java`, `AutoAttackController`: profile PK riêng của Hung và đường cast thường.
- `tools/luna/HungBySonModUpLv.java.in`: nối bộ lọc native, lựa chọn Quái/PK, giữ engine chiêu và vẽ vòng.
- `tools/luna/HungAutoPkSerpentPatcher.java`: ưu tiên Xà nữ (port, `findTarget`, API rank, bridge).
- `tools/luna/HungAutoPkLinesPatcher.java`: patch 365px + đường kẻ (chỉ policy và `paintPkRange`).
- `tools/luna/HungCacheFocusPatcher.java`: từ bản khóa mục tiêu, chỉ thay `paintPkRange` và predicate cache `isExcludedTemplate`; build/publish dùng `-CacheFocus`.
- `tools/luna/HungBySonAutoPkPatcher.java`, `HungAutoPkRangeHook.java`: patch incremental ghim nguồn; kiểm method không đổi, serializer PK và inverse hook vẽ.
- `tools/luna/build-hung-auto-pk.ps1`, `publish-hung-auto-pk.ps1`: tạo candidate tên mới, kiểm toàn bộ, backup và thay đúng endpoint.
- `fixtures/luna/AutoPkTargetPolicyHostTest.java`: hình học, tràn số, khử ID trùng và so top 20 với bộ full-sort tham chiếu.
- `fixtures/hung-by-son/HungBySonAutoPkBinaryTest.java`: target, mode, ưu tiên, FIFO, biên vùng, payload 20 người, MP/cooldown/buff, cast thường và race enqueue.
- `fixtures/hung-by-son/HungBySonAutoPkRangeUiTest.java`: pixel/scale/camera/clip và điều kiện hiện vòng trong MIDlet thật.
- `fixtures/hung-by-son/HungBySonAutoPkWorldRangeTest.java`: kiểm vị trí hook trong renderer, native transform/reset và đối chứng JAR cũ bị lệch tâm.
- `fixtures/hung-by-son/HungBySonAutoPkMenuStateTest.java`: bốn tổ hợp master/mode, nhãn checked/unselected, callback native và loại quái gần hơn người.
- `fixtures/hung-by-son/HungBySonAutoPkCadenceTest.java`: đối chiếu trace nhịp đánh quái giữa JAR nguồn và candidate.

Rebuild phải dùng đúng backup nguồn được ghim sau khi đã thay endpoint; không dùng JAR đầu ra làm input cho patch incremental này. X3 đóng lại từ x1 `896F3B69…` bằng `build-hung-pk-x3.ps1` (mỗi slot trạng thái riêng); các endpoint khác nằm ngoài nâng cấp này.

Để rebuild riêng sửa tâm vòng, dùng `build-hung-auto-pk.ps1 -RangeCenterFix -InputJar <backup bản Auto PK trước sửa tâm>`;
publisher tương ứng cũng nhận `-RangeCenterFix`. Không dùng JAR đã sửa tâm làm input cho patch ghim bản lỗi cũ.

Rebuild riêng nhãn checked/vòng xem trước dùng `-PreviewFix` với backup bản trước sửa xem trước;
publisher dùng cùng switch. Mỗi mode incremental có nguồn ghim riêng, không dùng JAR đầu ra làm input.

Rebuild riêng bán kính 400px dùng `-Radius400` với backup bản preview trước đổi bán kính.
Rebuild ưu tiên Xà nữ dùng `-PkSerpent` với backup `Hung_By_Son-before-hung-auto-pk-serpent-20261026-a.jar` (`A6981730…`).
Rebuild chặn Auto Thú cưỡi khi đang cưỡi dùng `-MountGuard` với backup `Hung_By_Son-before-hung-mount-guard-20261026-a.jar` (`BF1BB40C…`).
Rebuild sửa reset map/danh sách entity dùng `-MapResetRace` với backup `Hung_By_Son-before-hung-map-reset-race-20261026-a.jar` (`2EB9258B…`).
Rebuild khóa mục tiêu dùng `-TargetLock` với backup `Hung_By_Son-before-hung-auto-pk-target-lock-20261027-b.jar` (`E52366BD…`).
Rebuild ưu tiên HP thấp trong nhóm dùng `-HpOrder` với backup `Hung_By_Son-before-hung-auto-pk-hp-order-20261027-c.jar` (`A2154378…`).
Rebuild 365px + đường kẻ dùng `-PkLines365` với backup `Hung_By_Son-before-hung-auto-pk-lines365-20261026-a.jar` (`0227180C…`).
Phải sinh lại cả policy và painter do hằng số Java được inline; không chỉ thay mỗi trường RADIUS.
