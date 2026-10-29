# Hung By Son — Auto đánh độc lập

**Nâng cấp Auto PK bản đơn 2026-10-25:** xem [Auto PK và vòng chọn mục tiêu](HUNG_BY_SON_AUTO_PK.md).
Tài liệu đó thay phần mô tả Auto quái + PK / công tắc quái thường bên dưới đối với bản đơn mới:
Auto PK chỉ đánh người trong 365px (yêu cầu cập nhật 26/10), Auto chỉ quái giữ toàn map, vòng đặc biệt dùng chung engine.
Các đoạn bên dưới giữ mô tả đường cũ và các tính năng nền; X3 nhận nâng cấp Auto PK từ `8F248E43…` (26/10), mỗi slot trạng thái riêng.

## Cách dùng

Mở **Menu → Auto đánh >** trong menu MOD. **Auto tự đánh** bật/tắt chế độ đã
chọn. **Auto chỉ quái** chọn mode 7; **Auto quái + PK** chọn mode 6 và chỉ xét
người chơi hợp lệ theo bộ lọc PK native. Chọn một mode đồng thời bật tự đánh.

**Mode chiến trường** là tùy chọn bật/tắt riêng, mặc định tắt và không lưu.
Khi bật, hiển thị ngay tọa độ server của vật thể khác; Auto quái + PK bỏ qua
10 người chơi khác nhau bị mình kết liễu gần nhất, tên của họ hiện đỏ in đậm.
Danh sách giữ qua đổi map/khu, không đổi thứ tự khi kết liễu lặp, xóa khi
tắt mode/thoát game. Bỏ level 40 và Xà nữ khỏi danh sách; Xà nữ cũng bị loại
khỏi cache ở mọi map. Chi tiết và giới hạn kiểm thử:
[Mode chiến trường](HUNG_BY_SON_BATTLEFIELD.md).

**Delay (ms)** mở ô nhập số nguyên từ 1 đến 1000; lần đầu là 650. Hủy hoặc nhập
sai không thay giá trị. Giá trị sai hiện thông báo và quay lại cùng ô nhập.
Delay dùng chung cho **Auto Farm và Auto đánh**: đổi thành 1000ms thì cả hai
dùng ngưỡng 1000ms. Đổi delay không bật Auto đánh hoặc tắt Farm. Mode của Auto
đánh và delay được lưu; Auto đánh luôn tắt khi mở lại client.

**Tấn công đặc biệt** của bản đơn là tùy chọn riêng trong submenu Auto đánh, mặc định TẮT,
không lưu qua lần mở client. Nó đổi cách gửi đòn của **Auto đánh hoặc Auto Farm**
đang bật; chọn tùy chọn này không tự bật hai chế độ đó. BẬT dùng gửi trực tiếp và nhịp từ ModUpLv:
không chờ Delay(ms), animation hoặc native skill interval trước khi gửi. Chiêu
ID 7, 8, 19, 20 chỉ được chọn sau **hơn 600ms** từ lần gửi đặc biệt gần nhất;
mọi chiêu gửi qua chế độ này đều cập nhật mốc đó. Chiêu khác không chịu gate
600ms. Điều kiện đã học, cooldown, mana và bộ chiêu trên thanh kỹ năng đang chọn
của Hung vẫn áp dụng. TẮT trở lại luồng cast/animation và Delay đã đặt.

Khi đặc biệt BẬT, xét các ô chiêu native hợp lệ theo **ID chiêu giảm dần**, thay
cho xoay vòng ô. Chỉ dùng đòn mặc định ID 0 khi không có chiêu cấu hình nào sẵn
sàng. Chọn chiêu đã hồi/đủ MP trước, sau đó mới kiểm tra mốc 600ms: nếu chiêu
ưu tiên vẫn vướng mốc đó thì chờ, không đánh chiêu thấp hơn để làm mới mốc chờ.
Đây là khác biệt quan trọng với bản đặc biệt đầu tiên: việc thử chiêu khác sau
khi gate báo chưa đủ 600ms đã liên tục lùi thời điểm tung chiêu ưu tiên.
Không lấy toàn bộ chiêu đã học ngoài thanh kỹ năng như ModUpLv.

Chế độ đặc biệt giữ mục tiêu chính, bộ lọc quái/PK/chiến trường, khoảng cách,
số mục tiêu phụ và cách tạo danh sách target native của Hung. Việc gom 20 mục tiêu
trong tầm 20.000.000 của ModUpLv chỉ dùng khi bật **Tấn công quái thường** (xem
[tính năng từ ModUpLv](HUNG_BY_SON_MODUPLV_PORT.md)). Gửi qua serializer native,
đặt cooldown/trừ MP một lần, rồi hủy tag cast để callback animation cũ không
gửi trùng. Chuyển tùy chọn hủy cast đang chờ. Di chuyển, đổi world, chết và
dialog vẫn chặn đánh; khi đã dừng di chuyển, đặc biệt không dùng mốc 100ms của
chế độ thường. Ngoài bãi, Farm vẫn chọn mục tiêu, tầm và nhóm quái bằng luồng
native; tại bãi xem đoạn "Farm + đặc biệt tại bãi" bên dưới.
Khi Farm + đặc biệt cùng BẬT, chỉ phần vẽ nhân vật local dùng tư thế đứng yên
thay cho tư thế tung chiêu; các lớp hình phụ của cast được ẩn. Không xóa path,
đích chạy, vận tốc, trạng thái/counter cast hay dữ liệu animation trong mô phỏng.
Khởi tạo cast, callback native, HP/MP/damage và gửi đòn đặc biệt vẫn chạy đầy đủ.
Farm tự về làng, hồi sinh và đi lại khu up theo luồng native; trạng thái đi bộ
không bị bộ ẩn tư thế can thiệp. Khóa di chuyển tay sẵn có của Farm được giữ.
Actor từ xa và trạng thái chết giữ nguyên; tắt một trong hai tùy chọn khôi phục
phần vẽ native. Bản giữ đứng yên bằng cách xóa path mỗi frame đã bị thay thế
vì có thể làm nhân vật kẹt ở làng và ngắt xử lý cast.
**Farm + đặc biệt tại bãi** (bản 2026-10-23 tối): khi Farm đã về tới bãi đã lưu
(trạng thái 0, đúng map/khu, trong bán kính đứng yên 30 hoặc di chuyển 280),
nhân vật đứng yên và đánh giống Auto đánh đặc biệt. Không bật lại auto cũ của
game (vốn tự chạy về tâm/tới quái) và không chạy vòng tung mọi chiêu native có
animation; thay vào đó chọn quái gần nhất theo bộ lọc quái của Farm, giữ mục
tiêu còn sống, rồi gửi bằng bộ chọn chiêu đặc biệt (ưu tiên ID, mốc 600ms).
Mỗi đòn gửi được, hoặc lúc bãi trống, đặt lại bộ đếm 400 tick "không đánh →
dùng Bùa về làng"; đòn bị chờ/từ chối không tính. Con Ma chỉ được chọn để nhánh
giải captcha native xử lý. Chết, sai map/khu, bị đẩy xa khỏi bãi, hồi sinh và
đi về bãi giữ nguyên logic cũ. Tắt đặc biệt trả bãi về luồng Farm native.
Hai lần gửi đặc biệt bất kỳ (Auto đánh hoặc Farm) luôn cách nhau ít nhất 30ms
thời gian thực (ModUpLv: khung tối thiểu 28ms), không phụ thuộc tốc độ game hay update chạy bù. Đòn
đặc biệt của Farm vẫn gửi khi đang mở màn hình/dialog khác (ví dụ danh sách thành
viên bang), giống Farm native, nên không còn bị tính là "không đánh" rồi dùng Bùa
về làng. Auto đánh vẫn tạm dừng khi mở dialog như trước.
Tại bãi, việc "kẹt" được tính bằng thời gian thực thay cho 400 tick của Farm:
gửi được đòn, đang chờ hồi chiêu/MP/mốc 600ms/30ms, bãi trống hoặc có Con Ma đều
không phải kẹt. Mục tiêu bị từ chối 3 lần liền được bỏ qua 3 giây để đánh con
khác. Chỉ khi 15 giây không tiến triển, Farm mới được dùng lại cơ chế Bùa về
làng cũ. Con Ma luôn được ưu tiên. Cast tay treo quá 1,5 giây bị hủy. Auto Bán Rác
(túi gần đầy) và Auto Hồi Sinh "Tự về làng" vẫn đưa nhân vật về làng như cài đặt.
Lượt Farm được nhận diện bằng actor local và thread khởi tạo, dùng chung khóa
với thao tác đổi chế độ; không áp dụng cho đánh tay hoặc actor từ xa. Bật/tắt
đặc biệt hủy lượt Farm local đang chờ, không tắt Farm hay đổi cài đặt Farm.

Bật Auto đánh tự tắt Farm; bật Farm tự tắt Auto đánh. Tắt chức năng đang chạy
không tự bật lại chức năng còn lại. Các cài đặt quái, kỹ năng và tọa độ của
Farm được giữ. Auto đánh không sử dụng bộ lọc nhóm quái hoặc vòng xử lý Farm.
Mục tiêu, timer và chính sách chọn chiêu riêng; chỉ giá trị delay dùng chung.

Khi bật, HUD có dòng `Auto Đánh: Chỉ Quái [7]` hoặc
`Auto Đánh: Quái & PK [6]`. Hiển thị tuân theo nút thông tin native của client;
EXP và thông báo native giữ nguyên bố trí. Đổi map/khu, chết, mất kết nối,
màn hình ngoài world/hành trang hoặc dialog đang mở làm Auto đánh tạm dừng và bỏ lượt
đánh cũ. Khi world sẵn sàng trong cùng lần chạy, nó chọn lại mục tiêu và chờ
delay. Chức năng này không tự đăng nhập hoặc hồi sinh.

Mở **Hành Trang** từ world vẫn tiếp tục Auto đánh. Chỉ màn hình hành trang
native có world làm màn hình cha được cho phép; điều hướng bàn phím trong
hành trang không được coi là di chuyển nhân vật. Luồng cập nhật world có sẵn
vẫn dùng để chạy animation và gửi đòn, không thêm vòng đánh thứ hai.

**Map ID 48** không đọc, học, khôi phục hoặc ghi cache quái, kể cả flush khi
thoát/chuyển map. Quái native trên map này vẫn hoạt động bình thường. Record
map 48 từ phiên bản cũ bị bỏ khi nạp cache trên map khác và được loại khỏi
RMS ở lần ghi hợp lệ tiếp theo; dữ liệu các map khác được giữ.

## Nhịp và ưu tiên di chuyển

Controller mới có intent, mục tiêu, generation và đồng hồ riêng. Nó đọc các
đối tượng đang tải trên map, giữ mục tiêu còn hợp lệ và chọn mục tiêu gần nhất
khi cần đổi. Primitive chọn chiêu/cast/animation/packet vẫn là của game khi
Tấn công đặc biệt TẮT.
Mục tiêu người chơi HP <= 0 hoặc đã vào trạng thái chết bị loại dù object
vẫn còn trong khu. Tick tiếp theo bỏ target/cast cũ rồi chọn đối tượng hợp
lệ gần nhất; mode 6 không ưu tiên người hơn quái. Request đang chờ cũng kiểm
lại tính hợp lệ, nên HP0 bị chặn trước cả lần tick đổi target. Khi đổi do
mục tiêu chết, đòn mới chờ delay đã đặt như các lần đổi target thông thường.
Hotbar, kỹ năng đã học, HP/MP và cooldown là dữ liệu chung của nhân vật;
cấu hình riêng của Farm không quyết định chính sách của Auto đánh mới.

Khi đặc biệt TẮT, gate Auto đánh thường và gate tự đánh của Farm dùng `elapsed > delay`. Request của
Auto đánh và các lượt đánh do Farm khởi tạo dùng
`elapsed > max(delay, nativeSkillInterval)` theo thời gian thực. Không cộng
300ms từ donor. Cooldown, mana, animation và tick có thể làm request đến muộn
hơn delay. Request đánh tay và actor từ xa giữ minimum 650ms hiện hành, kể cả
khi Farm bật; cast Farm được nhận diện tại callsite tự đánh và thread khởi tạo.

Khi Auto đánh bật, phím hướng và lệnh đi trên đất/cửa map được ưu tiên. Trước
chỗ native chặn đi khi đang đánh, controller hủy lượt tự đánh đang chờ, nhả
trạng thái đánh và giữ chức năng ON. Khi còn giữ phím, chờ đường đi hoặc đang
đi, nó không chọn chiêu mới; callback cũ không gửi packet. Khi dừng, nhịp được
tính lại ở chế độ thường: đòn đầu sau di chuyển chờ **100ms**, độc lập delay đã đặt. Các đòn
sau quay lại delay đã đặt. Lượt khởi tạo đầu này và request của nó cùng dùng
minimum 100ms; cooldown, mana, animation và khoảng skill native vẫn áp dụng.
Không khởi tạo được chiêu thì giữ mốc 100ms cho lần thử kế tiếp. Phím điều hướng menu, nhấn quái/NPC
và thao tác editor không được coi là lệnh đi trên đất. Cast đánh tay/từ xa
không bị controller mới hủy.

Đối chiếu JAR Hung này xác nhận quái native dùng loại `cv=1`, người chơi dùng
`cv=0`; serializer tương ứng gửi opcode 9 và 6. Mapping khác bản HSOSKY trong
tài liệu protocol chung. Adapter giữ nguyên serializer của Hung; không đổi
opcode theo bảng của client khác. Fixture kiểm cả hai đường với byte payload
thật và bộ lọc PK native.

Chuyển quyền hoàn tất cleanup của chức năng cũ trước khi bật chức năng mới.
Cast được gắn generation; callback còn sót của lượt đã hủy không được gửi
packet hoặc biến thành request native không gắn tag. Farm update và cleanup
không được sửa combat trong thời gian Auto đánh mới giữ quyền. Riêng helper
HP/MP hiện có vẫn chạy theo cài đặt và throttle cũ khi world hợp lệ; Auto đánh
không bật hoặc thay ngưỡng HP/MP đó. Cấu hình mới
dùng MIDP RMS `hbs_auto_attack_v1`, ngoài namespace dọn dữ liệu native, chỉ
lưu version, mode và delay; không lưu enabled hoặc mục tiêu.

Cleanup đăng nhập của bản nền xóa cả hai store cấu hình Farm. Bridge mới giữ
đúng `sys_auto_cfg` và `sys_farm_loc` (tên vật lý `inv_…`), rồi chuyển mọi tên
còn lại qua guard EXP sẵn có. Cache phiên vẫn được dọn; dữ liệu tài khoản và
EXP giữ quy tắc bảo toàn hiện hành. Kiểm thử gọi phương thức cleanup native
thật và đối chiếu byte cấu hình Farm trước/sau.

## Bản X3

[Hung_By_Son_x3.jar](../../jar/Hung_By_Son_x3.jar) có ba client cùng logic trên.
Từ 2026-10-23, X3 đóng gói đúng bản đơn hiện hành nên có cả Tấn công đặc biệt và Farm + đặc biệt đứng yên tại bãi, riêng cho từng client.
Chọn `Hung By Son 1/2/3`; giữ `*` khoảng 500ms để về menu rồi chọn client khác.
Chuyển client giữ instance và vòng lặp của các client đã mở.

Mỗi slot có controller, mode, delay, mục tiêu, cast và RMS riêng. Delay dùng
chung giữa Farm và Auto đánh **trong cùng slot**, không đổi slot khác. Store
mới là `ahbs_auto_attack_v1`, `bhbs_auto_attack_v1`, `chbs_auto_attack_v1`;
tài khoản và Farm giữ namespace `ainv_`/`binv_`/`cinv_`. Bảng EXP theo level
vẫn dùng một backend/store chung như X3 trước đó. Đóng/mở slot làm Auto đánh
OFF và nạp lại setting; không reset bảng EXP chung.

## Build và kiểm chứng

Pipeline staging: `tools/luna/build-hung-auto-attack.ps1`. Nguồn Hung ghim
SHA-256 `188C481E59318B27468F29DE7420C90FE1E9027BD77A22B4710A312FD6BDB2AD`;
donor Bạch Hổ ghim
`A8A4165F0E78C7E852444A425CB8C0611BF772A70F6D44B1374091225732FF0E`.
Sau phát hành, rebuild phải truyền bản backup có hash nguồn này qua `-InputJar`.
Backup nguồn 188C là `../jar/backup/Hung_By_Son-before-hung-auto-attack-20261014-final-c.jar`.
Script chỉ tạo candidate và bằng chứng dưới `build/`, không thay release.

Pipeline X3: `tools/luna/build-hung-auto-attack-x3.ps1`, lấy đúng bản đơn đã
nghiệm thu và hồ sơ verified.json của nó cùng template launcher ghim hash,
rồi kiểm đủ ba namespace. `-StageFromVerifiedCandidate` cho phép build từ
candidate đúng hash nằm trong stage đã nghiệm thu khi endpoint đang bị giả lập
khóa; không thay JAR phát hành. Publisher X3 vẫn yêu cầu bản đơn đã phát hành
đúng hash và chứng minh chuyển hash bằng hồ sơ publish của bản đơn.
Publisher riêng cho bản đơn/X3 backup đúng endpoint
và đối chiếu các JAR khác cùng golden trước/sau thay file.

Fixture semantic kiểm chính sách, còn fixture binary phải bấm callback native,
chạy bridge/skill/request thật, kiểm chuyển quyền và RMS qua hai tiến trình.
Các suite cache, EXP, native timing, animation, menu và tính năng cũ chạy lại
trên candidate. Patcher kiểm symbol và inverse của các hook; archive verifier
kiểm class linkage, CLDC, manifest/resources và mọi class ngoài allow-list.

Hash phát hành, backup và kết quả thực tế được ghi duy nhất trong
[RELEASES.md](RELEASES.md). Binary/emulator không thay thế thử nghiệm gameplay
trên server thật; phạm vi nghiệm thu của lần này không gồm server thật.

Bằng chứng map48, hành trang và nhịp 100ms của bản hiện hành:
[báo cáo kiểm thử](HUNG_BY_SON_MAP48_INVENTORY_TESTS.md).
