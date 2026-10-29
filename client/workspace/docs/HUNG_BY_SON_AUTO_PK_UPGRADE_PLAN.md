# Hung By Son — hiện trạng và yêu cầu nâng cấp Auto đánh / Auto PK

Ngày ghi nhận: 2026-10-25. Đây là tài liệu bàn giao ngữ cảnh giữa các phiên.

**Trạng thái cập nhật 2026-10-26: đã phát hành bản đơn và sửa vòng lệch tâm.** Người dùng đã yêu cầu tiến hành nâng cấp sau khi lưu tài liệu này. [Hành vi hiện hành](HUNG_BY_SON_AUTO_PK.md), [báo cáo nghiệm thu](HUNG_BY_SON_AUTO_PK_TESTS.md), [release/hash/backup](RELEASES.md). Chưa thử PK mới trên server thật; X3 giữ nguyên. Các phần hiện trạng bên dưới mô tả JAR nguồn trước nâng cấp để giữ bối cảnh.

Sự cố tiếp theo được theo dõi riêng tại [điều tra tải map thiếu NPC/vật thể](HUNG_BY_SON_MAP_LOAD_INVESTIGATION.md): parser native có thể bỏ cả đuôi gói object khi ID map mới gặp entity map cũ chưa được dọn. Bản sửa phải giữ vòng đánh/mode/radius 400px; không suy diễn đây là lỗi của bộ chọn PK.

## 1. Mục tiêu và phạm vi

Đối tượng khảo sát là `client/jar/Hung_By_Son.jar`. Người dùng muốn giữ hiệu quả của vòng đánh đặc biệt khi đang bật “Tấn công quái thường”, đồng thời dùng được vòng đánh đó cho chế độ chỉ đánh người.

Hash JAR đối chiếu lúc ghi tài liệu: `E8A95AEBF84055D9137276792C7BD4D6E2468B21FF51A8B3C48E57CD8ACC2CFA`. Đây chỉ là mốc khảo sát; trạng thái phát hành chuẩn nằm ở [RELEASES.md](RELEASES.md). Phiên sau phải kiểm hash thực tế lại trước khi sửa/build.

Phạm vi yêu cầu đã nêu là bản đơn. Việc phát hành đồng thời X3 chưa được chốt; không tự coi X3 đã được nâng cấp.

## 2. Hiện trạng và vấn đề người dùng gặp

Menu hiện có Auto tự đánh, Auto chỉ quái, Auto quái + PK, Delay, Mode chiến trường, Tấn công đặc biệt và Tấn công quái thường.

Có hai đường đánh đặc biệt khác nhau:

| Nội dung | Đặc biệt BẬT, quái thường TẮT | Đặc biệt BẬT, quái thường BẬT |
|---|---|---|
| Chủ thể gửi đòn | Nhánh đặc biệt cũ của Auto đánh/Farm | Vòng ModUpLv độc lập từ canvas |
| Chọn chiêu | Thanh kỹ năng; ID cao trước, ID 0 dự phòng | Mọi chiêu đã học; thứ tự tối ưu khi bật khóa |
| Nhịp | Khoảng cách tối thiểu 30ms và gate riêng hơn 600ms cho 7/8/19/20 | Vòng 28ms thời gian thực, canh khóa mặc định 575ms |
| Trọng kiếm, ID 0 | Dự phòng; mỗi lần gửi cập nhật mốc đặc biệt | Chèn trước khi sẵn sàng; không tạo khóa và không làm mới gate 600ms trong chế độ khóa |
| Chiêu không có phản hồi | Không dùng cơ chế thử lại của vòng mới | Có thể xóa cooldown thử lại một lần sau 700ms không có trả lời |
| Mục tiêu | Bộ chọn và danh sách native | Tối đa 20 quái sống gần nhất toàn map đang tải |

Người dùng đo được khoảng **80 trúng/phút khi tắt quái thường**, và **137 trúng/phút khi bật**. Bytecode được khảo sát xác nhận hai đường thực thi khác nhau như trên.

Báo cáo server trước đó ghi nhận server thường bỏ qua im lặng đòn gửi quá sớm sau chiêu trước; khóa quan sát khoảng 500ms. Trọng kiếm không tạo khóa mới, nên có thể chèn rồi gửi chiêu khác ở khung sau. Vòng mới tận dụng điều này.

**Giới hạn bằng chứng:** con số 81–85 so với 137 trong báo cáo cũ là so sánh chế độ canh nhịp Tắt với Khóa 575ms bên trong vòng mới, không phải phép thử A/B riêng cho công tắc quái thường. Cơ chế giải thích phù hợp với quan sát của người dùng, nhưng chưa có phép đo mới để tách riêng ảnh hưởng của nhịp, bộ chiêu và mục tiêu.

### Vì sao chọn Auto quái + PK nhưng không đánh người

Khi đặc biệt và quái thường cùng BẬT, `AutoAttackController.tick()` nhường quyền cho vòng ModUpLv trước bước chọn mục tiêu quái/PK. Vòng ModUpLv chỉ nhận quái (`cv=1`), không nhận người chơi (`cv=0`) và không dùng lựa chọn PK của controller.

Do đó, ngay cả khi Auto tự đánh BẬT và Auto quái + PK được chọn, vòng mới vẫn không tự đánh người. Tắt quái thường có thể trả về đường PK cũ nhưng mất các tối ưu của vòng mới.

Vòng mới hiện có thể chạy khi bật đặc biệt dù Auto tự đánh đang TẮT; đây là hành vi hiện tại cần chú ý khi thiết kế quyền bật/tắt sau nâng cấp.

## 3. Yêu cầu cuối cùng đã thống nhất trong khảo sát

**Cập nhật mới nhất 26/10: bán kính Auto PK đã giảm từ 500 xuống 400px theo yêu cầu người dùng,
áp dụng đồng thời cho vòng xem trước và khoảng cách chọn mục tiêu thực tế, cả đường thường và đặc biệt.**

### 3.1. Menu và hai chế độ mục tiêu

- **Bỏ mục “Tấn công quái thường” khỏi menu.** Cơ chế của nó trở thành mặc định của tấn công đặc biệt; không để việc ẩn dòng menu vô tình rơi về đường đặc biệt cũ.
- Giữ **Auto chỉ quái**.
- Đổi **Auto quái + PK** thành **Auto PK**, chỉ đánh người đủ điều kiện PK, không đánh quái.
- Hai chế độ loại trừ nhau và cùng sử dụng vòng đánh mới. Chuyển qua Auto PK phải giữ cơ chế đánh như vòng quái hiện tại, thay phần chọn mục tiêu và cách gửi tương ứng với người chơi.
- Giữ các mục Auto tự đánh, Tấn công đặc biệt và Mode chiến trường; ngữ nghĩa bật/tắt chi tiết cần rà soát theo mục 5.

### 3.2. Phạm vi và giới hạn mục tiêu

| Chế độ | Tập mục tiêu | Phạm vi | Thứ tự thông thường | Giới hạn |
|---|---|---|---|---|
| Auto chỉ quái | Quái sống qua bộ lọc hiện tại | Toàn map như hiện tại, trên dữ liệu client đã tải | Gần nhân vật trước | Tối đa 20 |
| Auto PK | Người chơi qua bộ lọc PK native | Bán kính 400px từ nhân vật | Gần nhân vật trước | Tối đa 20 |

**400px chỉ áp dụng cho Auto PK. Không thu hẹp Auto chỉ quái về 400px.** Không trộn quái vào danh sách Auto PK. Không coi mọi người trên map là mục tiêu hợp lệ: vẫn loại bản thân, người chết và các trường hợp bị bộ lọc PK native từ chối.

Giới hạn 20 là giới hạn danh sách yêu cầu phía client; khả năng server chấp nhận 20 người và số mục tiêu thực sự nhận sát thương phải được kiểm chứng, không được hứa chỉ từ code client.

### 3.3. Vòng hiển thị phạm vi Auto PK

- Vẽ đường tròn xanh giống ảnh tham chiếu trong hội thoại, tâm ở vị trí nhân vật trên bản đồ, bán kính **400px**, đường kính 800px.
- Theo nhân vật và camera; dùng tọa độ world chuyển sang tọa độ màn hình. Phần ngoài màn hình được clip tự nhiên, không co vòng cho vừa màn hình.
- Chỉ biểu diễn vùng chọn mục tiêu Auto PK. Auto chỉ quái toàn map **không hiện vòng 400px**.
- **Cập nhật đã chốt 26/10:** người dùng chọn hiện vòng ngay khi Auto PK được chọn, kể cả master Auto và đặc biệt cùng OFF. Đây là xem trước vùng chọn, không bật đánh; Auto chỉ quái không hiện vòng. Bán kính mới nhất là 400px cho cả vẽ và chọn mục tiêu.
- Vòng là phạm vi chọn mục tiêu, không phải cam kết tầm sát thương do server cho phép.

### 3.4. Mode chiến trường + Auto PK ưu tiên level 40

Khi đồng thời bật Mode chiến trường và chọn Auto PK, sau bước lọc hợp lệ và giới hạn bán kính 400px:

1. Xếp người chơi **level đúng 40** trước, trong nhóm này gần trước.
2. Xếp người chơi level khác sau, trong nhóm này gần trước.
3. Lấy tối đa 20 người từ danh sách đã sắp xếp.

Nếu có hơn 20 người level 40 hợp lệ trong vùng, chọn 20 người level 40 gần nhất. Người level 40 không hợp lệ hoặc ngoài vùng không được ưu tiên vào danh sách.

Tắt Mode chiến trường thì Auto PK chỉ ưu tiên khoảng cách. Auto chỉ quái không bị thay đổi bởi quy tắc ưu tiên người level 40.

Giữ bộ lọc chiến trường đang có, gồm bỏ qua danh sách tối đa 10 tên đã bị mình kết liễu. **Hiện tại level 40 bị bỏ qua khi ghi danh sách đã kết liễu, không phải bị cấm chọn làm mục tiêu**; tránh diễn giải nhầm lúc sửa. Giữ màu tên, cách cập nhật tọa độ server và các hành vi chiến trường khác trừ khi có yêu cầu mới.

### 3.5. Cơ chế đánh cần giữ dùng chung

- Vòng 28ms theo thời gian thực, không phụ thuộc tốc độ game.
- Canh khóa mặc định 575ms; giữ các lựa chọn canh nhịp hiện có nếu không có yêu cầu thay đổi.
- Chọn toàn bộ chiêu đã học, điều kiện MP/cooldown, ưu tiên Trọng kiếm và thứ tự chiêu của vòng mới.
- Gate riêng của 7/8/19/20; trong chế độ khóa, Trọng kiếm không làm mới gate đó.
- Thử lại một lần với chiêu không được phản hồi theo quy tắc hiện tại; không trừ MP/cooldown hoặc đếm một lượt hai lần.
- Buff vẫn dùng bộ dựng mục tiêu bản thân/đồng đội và opcode 40, không chuyển thành đòn gây sát thương vào danh sách PK.
- Giữ thống kê tung/trúng/hụt, tích hợp Farm, nhặt đồ, phó bản và các cơ chế hiện có ngoài phạm vi thay đổi đã yêu cầu.

## 4. Phương án đã bỏ, không triển khai nhầm

Ban đầu người dùng hỏi thêm mục “Tấn công tất cả mục tiêu”, trộn 20 quái/người trong 500px. **Phương án này đã được thay thế** bởi yêu cầu bỏ quái thường và tách Auto chỉ quái / Auto PK ở trên.

Không thêm mục “Tấn công tất cả mục tiêu”, không trộn quái/người, không giới hạn quái toàn map xuống 500px.

## 5. Điểm kỹ thuật cần khảo sát và quyết định ở phiên triển khai

- **Quyền bật/tắt — đã chốt:** người dùng chọn giữ hiện tại: đặc biệt BẬT vẫn tự đánh dù Auto tự đánh TẮT. Đã triển khai và kiểm cả gói PK lẫn vòng vẽ khi master OFF; Farm/phó bản giữ quyền riêng.
- **Đặc biệt TẮT — đã xử lý:** đường Auto PK thường cũng chỉ đánh người trong 400px, qua bộ lọc native và ưu tiên chiến trường. Đường thường vẫn dùng cast/nhịp native; vòng đặc biệt dùng engine mới như yêu cầu. Không ngắt cast còn hợp lệ đang bận chỉ vì thứ tự mục tiêu đổi.
- **Serializer PK:** Hung dùng opcode 9 cho quái, opcode 6 cho người. Đối chiếu descriptor/symbol và payload thực tế, khả năng nhiều người trong một gói, giới hạn mục tiêu từng chiêu và câu trả lời PK. Không áp bảng opcode của client khác vào Hung.
- **Nhịp PK:** chưa có phép đo chứng minh khóa 575ms/Trọng kiếm đạt hiệu quả PK như đánh quái. Giữ thuật toán theo yêu cầu, kiểm chứng thực tế trước khi khẳng định throughput.
- **Khoảng cách:** thống nhất cùng tâm, đơn vị, tọa độ server/world giữa bộ chọn và vòng vẽ. Biên hiện tại là khoảng cách <=400px; đã kiểm 399/400/401 và dùng long để tránh tràn số. Policy và painter phải sinh lại cùng nhau vì Java inline hằng số RADIUS.
- **Bộ lọc:** tái sử dụng điều kiện PK native và chiến trường qua bridge phù hợp, không tự viết lại bằng phỏng đoán tên/màu. Ưu tiên level 40 chỉ chạy sau lọc.
- **Chuyển chế độ:** nhả target/cast cũ và ngăn callback gửi nhầm loại sau đổi Quái ↔ PK; không tạo hai luồng gửi. Duy trì đúng ownership với Farm, phó bản đứng chốt và mê cung độc quyền.
- **Lưu cấu hình và lệnh cũ:** rà RMS/mode đã lưu, callback menu, chat 6/7 và hook dùng tên cũ. Người dùng chưa yêu cầu đổi mặc định khởi động hoặc cách lưu setting.
- **Phạm vi phát hành:** xác nhận endpoint cần làm ở phiên triển khai; X3 cần kiểm riêng namespace, thống kê, mode và vòng vẽ từng slot nếu được đưa vào phạm vi.
- Hướng dẫn client hiện ghi giữ “Auto quái + PK”; yêu cầu người dùng trong tài liệu này cho phép đổi riêng Hung thành Auto PK. Không lan thay đổi sang endpoint/client khác chỉ vì dùng chung source.

## 6. Điểm đọc tiếp trong repository

Đọc hướng dẫn gốc và `client/workspace/CLAUDE.md` trước; xem kiến trúc/build/protocol theo chỉ dẫn đó. Các nguồn liên quan trực tiếp:

- [HUNG_BY_SON_MODUPLV_PORT.md](HUNG_BY_SON_MODUPLV_PORT.md): vòng đặc biệt mới, lịch sử khóa server, phép đo và giới hạn bằng chứng.
- [HUNG_BY_SON_AUTO_ATTACK.md](HUNG_BY_SON_AUTO_ATTACK.md): Auto đánh/Farm, đặc biệt cũ và quyền sở hữu. Một số đoạn mô tả phiên bản cũ; phải đối chiếu code và release hiện hành.
- [HUNG_BY_SON_BATTLEFIELD.md](HUNG_BY_SON_BATTLEFIELD.md): bộ lọc chiến trường, 10 tên và ngoại lệ level 40.
- [RELEASES.md](RELEASES.md): mốc phát hành, pipeline và bằng chứng.
- `src/luna/features/AutoAttackController.java`, `AutoAttackRuntime.java`: mode, menu, quyền gửi, nhường vòng mới và chọn chiêu cũ.
- `tools/luna/HungBySonModUpLv.java.in`: `frame`, `attackFrame`, `lockOrder`, `nearestMonsters`, `loopMonster`, Farm/phó bản/mê cung.
- `src/luna/features/SkillCastStats.java`, `tools/luna/HungBySonSkillStats.java.in`: khóa, phản hồi, retry và thống kê.
- `tools/luna/HungBySonSpecialAttackPatcher.java`, `HungBySonModUpLvPatcher.java`, `HungBySonCopySpecialPatcher.java`: hook/serializer; kiểm file và pipeline hiện hành trước sử dụng.
- `tools/luna/HungAutoAttackBridgeGenerator.java`, symbol catalog của build hiện hành: bộ lọc PK/native access; không sửa generated output bằng tay.
- `fixtures/hung-by-son/HungBySonCopySpecialBinaryTest.java`, `HungBySonAutoAttackBinaryTest.java`, `HungBySonBattlefieldBinaryTest.java`, `HungBySonAutoAttackPkCorpseHostTest.java`: điểm mở rộng kiểm chứng.

## 7. Checklist tiếp tục và nghiệm thu

- [x] Đọc tài liệu này, kiểm release/hash thực tế và các thay đổi workspace sẵn có; không reset/clean.
- [x] Khảo sát serializer/opcode 6 và bộ lọc PK; chốt quyền bật/tắt còn chưa rõ trước phần việc phụ thuộc.
- [x] Tách chính sách chọn mục tiêu Quái/PK và thứ tự chiến trường; giữ engine chiêu/nhịp dùng chung.
- [x] Bỏ dòng quái thường, đổi nhãn và ngữ nghĩa Auto PK; rà mode/RMS/lệnh cũ.
- [x] Vẽ vòng xanh Auto PK đúng tọa độ và bán kính 400px; không vẽ trong Auto chỉ quái.
- [x] Kiểm lựa chọn: 0/1/20/>20 mục tiêu, không trộn loại, người chết/bản thân/không thể PK, biên vùng, đổi map/khu/nhân vật.
- [x] Kiểm chiến trường: level 40 xa hơn vẫn đứng trước level khác trong vùng; >20 level 40; tên bị loại; tắt mode trả về gần trước.
- [x] Kiểm packet thật tại queue bằng fixture: opcode 6/9/40, payload, MP/cooldown/thống kê một lần, callback cũ bị chặn khi đổi mode.
- [x] Kiểm nhịp, retry và buff; Auto tự đánh OFF, đặc biệt OFF, di chuyển, dialog, chết/mất kết nối, Farm/phó bản/mê cung.
- [x] Chạy hồi quy, linkage/CLDC/resource và giả lập theo pipeline; chỉ build candidate tên mới, không ghi đè release chưa nghiệm thu.
- [ ] Đo server thật riêng cho PK: danh sách nhiều người, tầm, khóa, Trọng kiếm, phản hồi và trúng/phút; phân biệt với kết quả fixture.
- [x] Cập nhật tiến độ vào tài liệu này để phiên sau biết đã làm gì, lỗi còn lại và bước kế tiếp; chỉ cập nhật release record khi có phát hành thực tế.

## 8. Nhật ký bàn giao

- **2026-10-25:** Đã đọc hướng dẫn, khảo sát bytecode JAR và source/báo cáo; xác định vòng đặc biệt mới chỉ chọn quái nên chặn hiệu lực PK của controller cũ. Đã ghi toàn bộ yêu cầu và điểm cần kiểm chứng trong tài liệu này. **Chưa thay code, chưa build, chưa thay JAR, chưa chạy phép đo PK mới trên server.** Bước tiếp theo khi người dùng yêu cầu triển khai là mục 5 và checklist mục 7.
- **2026-10-25, triển khai theo yêu cầu tiếp theo:** Người dùng chọn giữ đặc biệt độc lập master. Đã nâng cấp bản đơn, thêm profile PK và policy top 20, vòng vẽ, bộ lọc/ranking và khóa chuyển mode. Stage nghiệm thu `build/hung-auto-pk-20261025-g/` đạt toàn bộ suite; A/B đủ 60s mỗi cấu hình Tắt và khóa575/retry khớp timestamp/skill/target/MP/cooldown với nguồn. Publisher backup và thay endpoint, xác nhận 16 JAR khác và golden không đổi. Xem báo cáo nghiệm thu để biết phạm vi chính xác. **Không cần triển khai lại phần client ở phiên sau; việc tiếp theo là phản hồi gameplay/đo PK server thật, hoặc nâng cấp X3 khi được yêu cầu.**
- **2026-10-26, sửa lỗi vòng lệch tâm từ ảnh người dùng:** Hai hook trước được đặt ở phần minimap sau reset camera. Đã chuyển về một hook trước reset world→screen; chỉ một class renderer đổi. Stage `build/hung-auto-pk-center-20261025-c/` đạt đối chứng bản cũ/camera native và toàn bộ hồi quy, rồi backup/thay JAR. Không đổi target/combat. Phiên sau phải giữ kiểm thử vị trí hook trong renderer cùng native transform/reset; test gọi painter trực tiếp với dịch camera thủ công không đủ chứng minh tâm trong world.
- **2026-10-26, sửa nhãn checked / vòng xem trước:** Ảnh tiếp theo lộ nhãn `Auto quái + PK [x]` còn sót ở trạng thái được chọn; đã sửa thành Auto PK [x]. Người dùng xác nhận muốn vòng hiện ngay khi mode PK được chọn, cả hai công tắc OFF cũng xem trước được. Stage `build/hung-auto-pk-preview-20261026-b/` đạt menu native đủ bốn trạng thái (75 ca), ảnh preview OFF/OFF, đối chứng nhãn cũ và toàn bộ hồi quy; đã backup/thay JAR. Chỉ hai method UI đổi, combat và tâm vòng giữ nguyên. Phiên sau không hỏi lại về việc hiện vòng khi OFF; đây là yêu cầu đã chốt.
- **2026-10-26, bán kính 400px:** Người dùng yêu cầu giảm cả vòng và khoảng cách chọn mục tiêu thực tế. Đã sửa nguồn policy, sinh lại bytecode painter chứa hằng số inline và kiểm gói native chỉ có người ở 400px, loại người ở 401px. Stage `build/hung-auto-pk-radius400-20261026-a/` đạt toàn bộ kiểm thử rồi backup/thay bản đơn. Đây là phạm vi hiện hành; các đoạn 500px trong lịch sử mô tả phiên bản trước. Quái toàn map, nhịp/chiêu, nhãn và preview OFF/OFF giữ nguyên.
