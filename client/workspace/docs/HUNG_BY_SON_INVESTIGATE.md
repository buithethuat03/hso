# Hung By Son: bản thử cache và chọn quái

Artifact: `client/jar/Hung_By_Son_investigate.jar`; nguồn tính năng là bản Hung
auto 500ms. Bản hiện hành dùng request gốc và gate auto 700ms, trạng thái phát hành xem
[RELEASES.md](RELEASES.md).
Hai biến thể 650ms/bản x3 mới nhất được phát hành thành `Hung_By_Son.jar` và
`Hung_By_Son_x3.jar`; xem [HUNG_BY_SON_X3.md](HUNG_BY_SON_X3.md). File Investigate
vẫn giữ profile 700ms riêng, cùng các chức năng quái mô tả dưới đây.
Bản hiện hành bổ sung chọn quái và tăng dung lượng cache; trạng thái
phát hành được ghi tại [RELEASES.md](RELEASES.md).
Bản thử có tên ứng dụng/vendor riêng và namespace RMS `inv_`; dữ liệu đăng
nhập/cài đặt/cache của nó tách khỏi `Hung_By_Son.jar`. Không sửa server.

## Cách test

Bản khôi phục thời gian giữ gate auto có sẵn của client gốc (strict >600ms),
và lấy khoảng request trực tiếp từ skill, không còn phần cộng thêm 300ms.
Bản 700ms được tạo theo yêu cầu vì dùng chiêu bị hụt. Theo yêu cầu sau đó,
bản mới đã kiểm tra khôi phục gate strict >600ms và request theo skill như
`HungSon-update.jar`; trạng thái thay file xem [RELEASES.md](RELEASES.md).
Theo yêu cầu đặt tên sau đó, bản 600ms được phát hành thành
`client/jar/Hung_By_Son_updated.jar`; file Investigate 700ms được giữ nguyên.
Tên ứng dụng và namespace RMS vẫn giống bản Investigate vì chỉ đổi tên file.
Profile Updated sau đó đổi gate auto thành strict >550ms thực và khoảng request
thành strict >max(550ms, khoảng skill), không cộng 300ms. Request đọc trực tiếp
`System.currentTimeMillis()` để tốc độ x1/x2/x3/x0.8/x0.9 không rút ngắn ngưỡng.
Nếu chiêu đến frame gửi khi chưa đủ thời gian, giữ frame đó và thử lại; khi đủ
thời gian mới gửi và cho animation đi tiếp. File Investigate 700ms giữ nguyên.
Hồi chiêu riêng vẫn chạy như trước. Gate không cam kết packet
đánh cách đều từng ấy ms: thời điểm thực tế còn phụ thuộc skill, animation
và tick. Cache/chọn quái/theo dõi HP và bản sửa quái xa phiên dài giữ nguyên.

1. Mở bản Investigate và đăng nhập. Lần đầu cache quái rỗng.
2. Chọn một map và một khu cụ thể; đi một vòng để nhận quái. Dòng `QCache`
   phải tăng `S` và `C` khi nhận dữ liệu mới type 1.
3. Đợi ít nhất một giây sau con cuối để dữ liệu được ghi, rồi thoát game.
4. Mở lại **cùng bản Investigate**, cùng server/map/khu. Đứng yên; quan sát
   `R`, `OK`, `W`, `X` và các quái xa đã gặp ở lần trước.
5. Sang khu khác, đi thu thập và thoát/mở lại. Kiểm tra mỗi khu khôi phục
   đúng dữ liệu của nó, kể cả khi ID quái trùng giữa hai khu.
6. Vào **Cài đặt cấu hình → Auto Tự Đánh → Chọn quái**, chọn một hoặc nhiều
   dòng `Tên quái - Lv N`. Dấu `[x]` đánh dấu nhóm đã chọn; chọn lại để bỏ.
   **Xong** quay lại menu Auto. Mỗi lần thay đổi được lưu ngay.
7. Auto phải chỉ chọn đúng tên và level đã chọn. Đổi khu trong cùng map,
   rồi thoát/mở lại để kiểm tra lựa chọn vẫn còn. Sang map chưa có lựa chọn
   phải đánh tất cả như thường. Chọn **Tất cả quái** để xóa bộ lọc map đó.
8. Trong **Menu Mod → Hiện thông tin**, tắt để ẩn cả hai dòng QCache và nền
   đen, bật lại để hiện. Cache và lựa chọn quái vẫn hoạt động khi HUD ẩn.
9. Đứng xa một bãi quái đã học, để người chơi khác giết quái. Theo dõi qua
   ít nhất hai chu kỳ hồi sinh mà không đi đến gần. Quái chết không được
   đánh; khi request 7 xác nhận HP dương, quái phải hiện và auto lại như
   nhóm đã chọn. Chỉ số `W` có thể tăng trong lúc kiểm tra lại.
10. Khi đánh chung và gặp con vẫn hiện HP nhưng đánh không giảm, theo dõi
    auto: sau ít nhất ba request đánh mục tiêu đó và 2,5s không đổi HP,
    client tạm chuyển mục tiêu và hỏi lại chi tiết. Nếu HP trả về vẫn dương
    và không đổi, con đó được chọn lại sau 3s; nếu HP bằng 0 thì xử lý chết
    và hồi sinh theo cơ chế ở trên.
11. Đứng xa bãi đã học, giữ nguyên map/khu ít nhất 1–2 giờ hoặc qua đêm trong
    lúc người khác liên tục giết quái. Khi có timeout/mismatch, client phải
    tự kiểm tra lại sau cooldown, không cần chạy gần hoặc ra/vào map. Nếu
    còn mất quái, ghi lại `QCache` và thời điểm; `X` là tổng lỗi, không tự
    giảm khi đã phục hồi, còn `W` chỉ đếm lượt đang chờ phản hồi.

Khu chưa được thu thập không có record để khôi phục. Bản trước cũng đã
khóa cache theo server/map/khu; bản bổ sung giữ quy tắc này, kiểm riêng việc
học nhiều khu và tăng giới hạn lưu. Không dùng ID từ khu khác để tạo quái.

Báo lại server, tên map, số khu, dòng QCache trước khi thoát và sau khi mở
lại, cùng hiện tượng quái xa/auto. Không cần gửi tài khoản hoặc mật khẩu.

## Chỉ số trên màn hình

QCache dùng cùng cờ **Hiện thông tin** với HUD native của menu MOD.

| Chỉ số | Ý nghĩa |
|---|---|
| `S` | Tổng record quái đang lưu trong các context của cache |
| `R` | Số lần tạo placeholder trong lần vào map hiện tại, bao gồm nạp lại object đã mất |
| `OK` | Số lượt xác nhận chi tiết hợp lệ của object chờ kiểm tra trong lần vào map này; một ID có thể được xác nhận nhiều lượt |
| `W` | Lượt đang chờ xác nhận chi tiết; timeout/mismatch kết thúc lượt và dọn entry, thời gian nghỉ trước lượt sau không được tính vào W |
| `X` | Lỗi cache/lưu dữ liệu, mismatch hoặc timeout; không chỉ riêng lỗi ID |
| `M`, `Z` | Map ID và số khu hiển thị từ 1 |
| `C` | Số record của server/map/khu hiện tại |
| `H` | Record có template hoặc tọa độ được packet 4 cập nhật khác dữ liệu cũ |
| `K` | Quái đã xác nhận đang được giữ thành xác chết trong map/khu; giảm khi detail HP dương xác nhận hồi sinh |

Lần đầu đi thu thập có thể `R=0, OK=0`: đây là quái server nạp bình thường,
không phải quái khôi phục từ cache. Khi vào lại, `R` cũng có thể nhỏ hơn `C`
vì các con gần đã được server nạp trước thời điểm khôi phục.

## Hành vi đã triển khai

- Ghi record thực sự nhận từ packet 4/type 1; không lưu người chơi/type 2.
  Lưu ID instance, template, vị trí và level đã biết; không lưu HP hay cờ
  sống/chết/đã xác nhận làm dữ liệu cho phiên mới.
- Packet 4/state 127 là trạng thái tạm thời dùng cờ chuyển động/animation,
  không phải thông báo xóa quái. Không dùng record này để học template/vị trí
  mới, nhưng giữ dữ liệu của ID đã học trước đó.
- Cache khóa theo endpoint host/port thực tế + map ID + khu zero-based.
  Không lấy ID của khu đã học để áp vào khu chưa từng học.
- Sau map/catalog sẵn sàng 800ms, khôi phục tối đa bốn object mỗi tick.
  Placeholder bị chặn khỏi auto qua các cờ native; native request retry của
  placeholder được vô hiệu hóa để dùng một nhịp hỏi lại có giới hạn.
- Chỉ hỏi ID đã lưu, không quét ID chưa biết. Request 7 cách nhau ít nhất
  400ms; tối đa hai lần mỗi ID trong một lượt. Timeout 8s tính từ lần hỏi đầu tiên, không
  tính thời gian chờ trong hàng đợi.
- Xác nhận yêu cầu object còn đúng identity/context, có request trong phiên
  hoặc được packet 4 thông báo, tọa độ chênh tối đa 96 mỗi trục và level khớp
  khi đã biết. HP nhận từ native packet 7 phải nằm trong khoảng hợp lệ.
- Theo dõi quái đã được xác nhận trong map/khu hiện tại, kể cả quái được
  nạp native. Khi object bị mất hoặc giữ trạng thái chết/ẩn, hỏi lại chi tiết
  sau thời gian chờ; object bị mất được tạo placeholder, object còn tồn tại
  được kiểm tra lại tại chỗ. HP bằng 0 tiếp tục giữ chết; chỉ phản hồi HP
  dương hợp lệ mới bỏ cờ ẩn, reset animation chết và nhận vị trí từ server.
- Quái đã xác nhận chết hoặc bị native đánh dấu loại khỏi world được giữ
  nguyên object thành xác chết nhìn thấy được. Animation chết chạy đến khung
  cuối rồi giữ lại; chặn cả nhánh xóa của world và nhánh dọn riêng trong
  cập nhật animation. HP0/trạng thái chết/cờ native chặn auto chọn nó.
  Vẫn hỏi detail theo nhịp đã giới hạn; chỉ detail HP dương hợp lệ mới phục
  hồi trạng thái sống và dọn toàn bộ cờ/counter chờ xóa. Không lấy HP tối đa
  làm HP hiện tại. Object chưa từng xác nhận giữ cách chặn hồi sinh dự đoán
  cũ, không vẽ thành xác chết dựa riêng vào dữ liệu cache từ phiên trước.
- Theo dõi tối đa tám record mỗi tick, tối đa một lần khôi phục lại mỗi tick.
  Giữ giới hạn request 400ms và hai lần mỗi lượt kiểm tra. Phản hồi vẫn chết
  được kiểm lại sau 3s; timeout, mismatch hoặc gửi lỗi lùi 20s/40s/60s. Sau
  ba lượt lỗi vẫn tiếp tục thử lại, tối đa một lượt mỗi 60s cho ID đó, không
  dừng vô hạn trong map. Mỗi lượt lỗi được dọn khỏi pending ngay; giữ record
  và cooldown để thử lại trong cùng map/khu. Placeholder ban đầu lỗi tạo
  hoặc xác nhận cũng được thử lại theo cơ chế này. Không quét ID chưa biết.
- Detail sai vẫn bị từ chối theo identity/vị trí/level/HP. Chỉ xóa placeholder
  thuộc cache chưa được packet 4 tiếp nhận; giữ nguyên object native hoặc
  placeholder đã được server tiếp nhận, chặn HP sai cho đến khi xác nhận
  hợp lệ. Phản hồi hợp lệ đến muộn có thể kết thúc cooldown; HP0 quay về
  chu kỳ 3s. Không lưu cooldown hoặc trạng thái chờ vào RMS.
- Đọc HP quái độc lập trước các nhánh native xử lý người đánh/animation,
  kể cả người đánh có object nhưng module animation chưa sẵn sàng. Observer
  đọc đủ danh sách, effects và footer trước khi áp HP cho quái đã xác nhận;
  packet thiếu/hỏng không được observer áp một phần. Mark/reset giữ nguyên
  vị trí stream để native xử lý tiếp; PK không gọi observer này. Native
  animation và combat vẫn dùng đường xử lý cũ.
- Khi Auto bật, theo dõi mục tiêu chính của request đánh quái được tạo đủ
  và xếp hàng gửi thành công. Ba request cùng identity và ít nhất 2,5s HP
  không đổi kích hoạt kiểm tra request 7. HP tiến triển hoặc phản hồi chi
  tiết mới reset bộ đếm. Không đếm request PK, danh sách rỗng, Auto OFF,
  serialization/gửi lỗi hay chỉ thử tung chiêu nhưng bị cooldown chặn.
- Kiểm tra mục tiêu còn HP dương không tự gán HP về 0, không thay animation
  hay các cờ chết. Chỉ tạm loại đúng object khỏi chọn/giữ/gom target của
  auto và xóa target đang giữ nếu còn đúng identity. Phản hồi HP giảm cho
  chọn lại ngay; nhiều lượt liên tiếp vẫn không tiến triển thì thời gian
  nghỉ tăng 3/6/12/24/30s, tối đa 30s rồi cho thử lại. Phản hồi không đổi đến
  trễ không kéo dài lượt nghỉ đã kết thúc. HP tiến triển reset lịch sử nghi
  ngờ; lịch sử cùng identity vẫn nằm trong bảng 32 entry có giới hạn.
  Có tối đa 32 mục tiêu tạm loại trong phiên; không lưu bộ đếm/cooldown này
  vào RMS. Cùng nhịp request 400ms, tối đa hai lần mỗi lượt và timeout 8s
  tính từ lần hỏi đầu; không giữ entry pending vô hạn khi lỗi.
- Packet 4 authoritative có thể thay placeholder nếu ID/template/vị trí
  không khớp. Timeout/mismatch chỉ xóa object do cache tạo, không xóa object
  native mới. Quái đã chết vẫn giữ trạng thái chết sau xác nhận.
- Ghi RMS debounce 500ms, flush khi đổi map và thoát game đúng lifecycle.
  Force-kill ngay trước khi flush có thể mất các record trong 500ms cuối.
- Bản bổ sung giới hạn 256 record/khu, 128 context gần nhất và blob tối đa 128KiB.
  Khi vượt giới hạn byte, bỏ các context cũ trước, giữ context hiện tại.
  Đây là giới hạn của prototype, chưa phải cache không giới hạn mọi khu.
- Danh sách chọn quái gộp chính xác theo tên + level, không theo ID instance.
  Nó lấy các object hiện có, nhóm đã học ở các khu của cùng map và nhóm đã
  chọn trước đó. Level chưa biết chưa được liệt kê.
- Bộ lọc lưu RMS riêng, khóa theo endpoint + map, không theo khu. Chọn nhiều
  nhóm cùng lúc; map chưa có cấu hình hoặc bỏ hết nhóm sẽ đánh tất cả.
  Có tối đa 128 profile map, 128 nhóm/profile và blob tối đa 128KiB. Lỗi lưu
  hiện thông báo rằng lựa chọn chỉ áp dụng trong phiên hiện tại.
- Bộ lọc tác động đến chọn/giữ mục tiêu auto và cả hai vòng gom mục tiêu phụ.
  Mục tiêu ngoài nhóm bị chặn trước khi tạo DTO và không chiếm suất target.
  Khi bật bộ lọc, target đang giữ bị xóa nếu không còn phù hợp. Auto OFF và
  các mục tiêu người chơi vẫn giữ hành vi native.
- Giữ nhịp combat gốc của bản hiện hành: auto gate strict >600ms, request
  theo nativeInterval, cùng tempo, Noclip, Auto Login và các tính năng khác.

## Kiểm tra và giới hạn

217 host checks kiểm cache qua classloader/phiên, context, hàng đợi lớn,
request pacing, xác nhận, timeout, dữ liệu hỏng, giới hạn và xung đột native,
cùng ID ở hai khu, 40 khu đã học qua restart, quái chết/ẩn, object mất,
nhiều lượt phản hồi HP0 rồi HP dương, backoff/lỗi, ownership và ready/scope.
Trong đó có mô phỏng 4 giờ cùng map với 41 ID, mỗi phút chết/mất object,
20 phút liên tục mất phản hồi/sai vị trí, rồi khôi phục toàn bộ khi chi tiết
hợp lệ trở lại. Kiểm thêm lỗi tạo ban đầu, timeout sau packet 4 tiếp nhận,
lỗi gửi, phản hồi HP0/HP dương muộn (kể cả object đã được packet 4 tiếp
nhận nhưng chưa từng được xác nhận) và dọn W; không dùng đổi map để phục hồi.
46 selection checks kiểm
gộp nhóm, nhiều lựa chọn, lưu qua phiên, map/server riêng, khu dùng chung,
menu cũ khi đổi map, dữ liệu hỏng và lỗi ghi.
Binary test kiểm CRC/archive, hook và đối chiếu ngược toàn bộ class native,
242 class links, 2087 member references mới, RMS riêng thực tế qua Memory RMS
và trạng thái pending/alive/dead của bridge. Các hồi quy tempo, Noclip,
combat, local MOD, cường hóa/NPC/đi buôn, semantic/controller/route và startup
giả lập 12 giây đạt.

Selection binary fixture bấm qua dispatcher popup native mà bàn phím/touch
sử dụng, kiểm marker và target đang
giữ, chạy primary cùng hai nhánh vector mục tiêu thật với metadata skill giả,
kiểm Auto OFF/PK/map chưa cấu hình, RMS cache/selection riêng và các dòng
128/129. Row callback truyền hai số, giải mã byte chỉ số theo unsigned;
không dùng object payload bị dispatcher popup bỏ qua.
HUD binary fixture gọi handler native của nút thông tin và
kiểm ON → OFF → ON tại ranh giới vẽ QCache.
36 native recovery checks gọi handler chết thật với người đánh chưa được
nạp, kiểm quái ẩn, chặn hồi sinh dự đoán khi chờ chi tiết, HP0 rồi HP37,
packet 8 và object thay thế, ba lượt mất phản hồi liên tiếp, dọn pending,
chặn hồi sinh dự đoán trong cooldown và phục hồi cùng identity không cần
packet 4 mới. Có thêm 12 giờ mô phỏng giữ một identity qua hàng trăm chu kỳ
chết/hồi sinh và 20 phút mất phản hồi, chạy nhánh xóa được trích từ world
native cùng hàm animation thật. Lời gọi cập nhật di chuyển/render trong
nhánh world bị thay trong harness; bảng frame mẫu và đồng hồ giả không
thay thế thử hình ảnh/đăng nhập trên server thật.
99 stale-target host checks kiểm ngưỡng request/thời gian, HP tiến triển,
thời gian nghỉ, cleanup khi lỗi, dùng chung nhịp request với hồi sinh,
Auto/PK/scope/identity, sáu lượt tăng cooldown, reset khi HP tiến triển,
observer giữ nguyên stream và parser dữ liệu. 16 native checks gọi serializer
và receiver thật, kiểm request xếp hàng thành công/lỗi, target chính unsigned,
người đánh chưa nạp hoặc đã nạp nhưng thiếu module animation, effects/footer,
packet thiếu và giới hạn HP. Fixture cố ý để module đó rỗng: native in lỗi
animation như trước, nhưng observer vẫn nhận HP chết; JAR cũ thất bại ở
kiểm tra tương ứng. Đây là lỗi gây dựng trong harness, không phải log game thật.

Người dùng đã xác nhận các chức năng hoạt động trong thử nghiệm ngắn, nhưng
báo quái xa mất sau thời gian dài và xuất hiện lại khi vào lại đúng map/khu.
Fixture đã tái hiện cơ chế dừng vĩnh viễn sau ba lỗi trên JAR trước sửa.
Bản sửa phiên dài ngày 2026-10-06 chưa được thử trên server thật; các kiểm
tra mô phỏng không thay thế thử qua đêm. Các kiểm tra tự động không chứng
minh ID ổn định qua phiên hoặc server trả đủ chi tiết quái xa. Cache chỉ bao
phủ những con đã được bản thử ghi nhận. Bộ lọc chặn target client gửi; chưa
đo việc server tự áp damage lan ra ngoài danh sách target.
Packet 7 không có template ID; kiểm tọa độ/level là điều kiện thận trọng,
không chứng minh tuyệt đối template khi server tái sử dụng ID.

Source: `src/luna/api/MonsterCacheAccess.java`,
`src/luna/features/MonsterCacheSupport.java`,
`src/luna/features/MonsterTargetWatchSupport.java`,
`src/luna/api/MonsterSelectionAccess.java`,
`src/luna/features/MonsterSelectionSupport.java`,
`tools/luna/InvestigateStorage.java.in`, `HungBySonInvestigatePatcher.java`.
Boundary chọn quái: `tools/luna/HungBySonMonsterSelectionPatch.java`.

Hung x1/x3 phát hành ngày 2026-10-28 bổ sung lọc chính xác tên `Lửa thiêng` và
`con Ma` khỏi popup **Chọn quái** (trim, không phân biệt hoa/thường, mọi level).
Lọc chung tại bước thêm dòng nên áp dụng cho object đang hiện, danh sách nhớ từ
cache và nhóm đã chọn. Khi nạp lựa chọn cũ, hai vật thể bị bỏ qua; các nhóm quái
hợp lệ giữ nguyên, profile chỉ gồm vật thể trở về Tất cả quái. Không loại `Xà nữ`
khỏi menu. Đây là bổ sung cho cache xác đã lọc trước đó, không đổi luật đánh.
Boundary nhận HP/theo dõi request: `tools/luna/HungBySonStaleTargetPatch.java`.
Boundary khôi phục thời gian gốc: `tools/luna/HungBySonNativeTimingPatcher.java`;
đối chiếu bằng `fixtures/hung-by-son/HungBySonNativeTimingHostTest.java`.
Build: `tools/luna/build-hung-investigate.ps1`, mặc định `-AutoGateMs 600`;
`-AutoGateMs 700` để tái tạo profile trước. Log và publication record
theo [RELEASES.md](RELEASES.md).

Profile Updated 550ms thời gian thực: `tools/luna/HungBySonRealTimeCombatPatcher.java`,
fixture `fixtures/hung-by-son/HungBySonRealTimeCombatHostTest.java`, build riêng
`tools/luna/build-hung-updated-real550.ps1`. Input được pin vào bản Updated 600ms
trước sửa trong `../jar/backup/Hung_By_Son_updated-before-real550-20261006.jar`.
Chỉ boundary request/auto và guard bước animation liên quan được thay đổi;
không rebuild tính năng Investigate hay sửa artifact Investigate 700ms.
