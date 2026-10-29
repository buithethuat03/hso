# Hung_By_Son: tải object trong map

Cập nhật 2026-10-26: đã sửa lỗi native bỏ cả đuôi packet 4 khi gặp entity map cũ
chưa dọn và lỗi null ở nhánh state 127. Phân biệt entity map cũ với entity chết
cùng map, bao phủ cả danh sách NPC -50. Xem [báo cáo tái hiện/kiểm thử](HUNG_BY_SON_MAP_LOAD_INVESTIGATION.md)
và [release hiện hành](RELEASES.md); nội dung bên dưới là phân tích protocol lịch sử.

Kiểm tra bytecode ngày 2026-10-04 trên `client/jar/Hung_By_Son.jar`,
SHA-256 `97DB01AD7E5550D4B8B5D747C7D0F870CF21972F03E9ABBEAB757D2DC3AEDACC`.
Đây là phân tích tĩnh client; chưa có trace phiên chơi server thật.

Ngày 2026-10-05 tái kiểm tra phần nhận object, entity và quái trên bản auto
500ms, SHA-256 `490AE902E7F4D3975663123283F4E93509CFA263E9E5E7D482DD078B500DCA1F`.
Ba class tương ứng `object-packets`, `entity`, `mob` có byte giống hệt bản
97DB ở trên, đối chiếu với backup trước Noclip. Các ánh xạ dưới đây áp dụng
cho bản hiện hành; chưa capture phiên chơi.

## Kết luận

Client không dùng khoảng cách tới nhân vật để giới hạn danh sách object đang
cập nhật. Nó giữ và cập nhật những object mà server đã thông báo. Việc vẽ có
lọc theo camera, độc lập với việc giữ object và chọn mục tiêu.

Có thể sử dụng object ở xa đã được server gửi mà không đi tới gần. Không thể
kết luận client hiện tại tải được tất cả object sống trong map: nếu server chưa
thông báo ID/toạ độ của object ở xa, mở rộng phạm vi chọn mục tiêu không tạo ra
dữ liệu đó. Các request chi tiết tìm thấy đều cần ID cụ thể đã biết.

## Luồng dữ liệu

| Packet | Chiều | Xử lý đã xác minh |
|---|---|---|
| 12 | Nhận | Chuyển/nạp map: reset trạng thái, map ID, vị trí vào map, các khối dữ liệu địa hình và trang trí. |
| 4 | Nhận | Đọc các record `type:byte, template:short, id:short, x:short, y:short, state:byte` cho đến hết dữ liệu. Tra object bằng `(id,type)`, tạo khi chưa tồn tại, cập nhật khi đã có. |
| 5 | Gửi/nhận | Client xin thông tin người chơi bằng ID short; phản hồi bổ sung tên, hình dáng/trang bị, chỉ số và trạng thái. |
| 7 | Gửi/nhận | Client xin thông tin quái bằng ID short; phản hồi bổ sung thông tin quái và đặt cờ `cX=true`. |
| 8 | Nhận | Đánh dấu `cM` cho object rời danh sách; world update đặt `cL` sau năm tick rồi loại khỏi vector. |
| -44 | Gửi | Xin thông tin object type 2 bằng ID short. |

Trong packet 4, object mới type 1 đi qua factory quái và gửi request 7 ngay;
type 0 tạo placeholder người chơi rồi gửi request 5; type 2 tạo placeholder
rồi gửi request -44. Các nhánh này không kiểm tra khoảng cách tới nhân vật.
Factory quái chọn lớp theo catalog/template, không chỉ tạo một lớp quái chung.

Packet 4/state 127 không phải tombstone xóa quái: với object có sẵn nó đặt
`ea=true` và `eb=y`; với type 1 chưa tồn tại nó không tạo mới. Cache cần bỏ
qua dữ liệu chuyển trạng thái này khi học template/vị trí và giữ record cũ.

Vòng đời chết/hồi sinh: quái thường có thể còn trong vector nhưng mang
`cG=4` và `dk=true`, khiến nó bị ẩn. Nhánh native `a_()` có thể hồi sinh dự
đoán từ `dB`/`aX` bằng `r()`, reset cờ và gán `bs=bt`. Quái đặc biệt có
nhánh đặt `cL` sau animation; packet 8 cũng có thể dẫn đến removal. Vì thế
chỉ kiểm “object còn tồn tại” chưa đủ để xác định quái đã hồi sinh.

Thông báo chết có ID người đánh chưa được nạp vẫn gán HP quái về 0, nhưng
helper tạo animation trả sớm nếu không tìm được người đánh. Đây là nhánh
khác khi người chơi khác đánh từ xa. Chưa có trace server để gán chắc chắn
một opcode cụ thể cho sự cố người dùng báo; fixture cần kiểm cả quái bị ẩn
và quái bị loại khỏi danh sách.

Receiver kết quả đánh dùng `(packet, byte, targetType)` có nhánh khác: nó
tìm người đánh type 0, rồi trả về ngay nếu người đánh chưa tồn tại hoặc có
`cL=true`, trước khi đọc HP từng mục tiêu. Đây là một đường xác định ở client
có thể giữ HP cũ khi người khác đánh quái; không cần giả định code server
trong workspace là server của phiên chơi. Nhánh bổ sung của Investigate
đọc đầy đủ phần còn lại: skill/count, mỗi ID/damage/HP/effects, rồi footer
HP/MP/auxiliary; chỉ áp dụng HP cho quái đã xác nhận, không áp dụng PK hoặc
packet bị cắt giữa chừng. Xem [release hiện hành](RELEASES.md).

## Lọc thông tin quái

Duyệt vector entity và giữ object `cv == 1` để phân biệt quái với người chơi
(`cv == 0`). Dữ liệu nhận có hai giai đoạn: packet 4 thông báo object và
packet 7 bổ sung chi tiết của ID đã biết. Không cần can thiệp combat để đọc
danh sách này.

| Thông tin | Trường/nguồn đã đối chiếu | Ý nghĩa |
|---|---|---|
| ID từng con | `Entity.cu` | ID instance từ packet 4; khóa tra object là `(id,type)` |
| ID loại quái | template short trong packet 4; `Mob.B` | Dùng tra catalog; nhiều con có thể cùng template |
| Tên | `Entity.cC` | Factory lấy tên từ catalog và truyền vào constructor entity |
| Level | `Entity.bz` | Packet 7 đọc unsigned byte rồi gán vào short này |
| HP hiện tại / tối đa | `Entity.bs` / `Entity.bt` | Packet 7 đọc hai int; combat/HP packets còn cập nhật sau đó |
| Tọa độ | `Entity.aZ` / `Entity.ba` | Packet 4; chi tiết packet 7 còn cập nhật đích `bg` / `bh` |
| Đã nhận chi tiết | `Entity.cX` | Packet 7 đặt true; trước đó không coi level/HP mặc định là dữ liệu hoàn chỉnh |
| Chuỗi bổ sung | `Mob.R` | UTF riêng trong packet 7; không đồng nhất với tên quái |

Catalog quái được nhận ở opcode 26: số record short, mỗi record có template
ID short, tên UTF và các thuộc tính mẫu. Client lưu vào `Mob.L`, tra bằng
template ID rồi tạo các lớp quái khác nhau. Vì vậy tên không bắt buộc xuất
hiện lại trong từng record packet 4.

Có thể tạo snapshot gồm `id, templateId, name, level, hp, maxHp, x, y,
detailsLoaded`. Khi chưa nhận packet 7, giữ ID/template/tọa độ và ghi
level/HP là chưa có; khi chi tiết về thì cập nhật. Phải bỏ record rời/xóa
khu và reset danh sách theo map/khu hiện tại.

Bằng chứng trong dump: packet 4 từ `object-packets.javap.txt:1013`, packet
7 từ `:2183`, catalog từ `:6446`; switch dispatch opcode 4/7/26 ở
`dispatch.javap.txt:11503`; factory quái từ `mob.javap.txt:139`, constructor
gán ID/tên/type/tọa độ ở `entity.javap.txt:2905`.

`cs.b(byte[])` đọc kích thước map và tạo các mảng tile/collision cho toàn bộ
`width * height`. `cs.c(byte[])` đọc danh sách trang trí cùng toạ độ và footprint
collision. Các dữ liệu này không phải danh sách quái/người chơi đang sống.

## Cập nhật, tải bổ sung và vẽ

- `World.a()` duyệt vector entity, xử lý cờ rời/xoá và gọi `entity.a()` cho
  từng entry còn tồn tại; không có kiểm tra camera hay khoảng cách ở bước này.
- Nhiều lớp quái có retry request 7 khi `!cX`, cách nhau ít nhất 5.000 ms.
  Đây là bổ sung thông tin cho ID đã biết, không phải khám phá ID mới toàn map.
- `Entity.c(entity)` so sánh x/y với camera, kích thước màn hình và kích thước
  sprite; World dùng kết quả này để quyết định vẽ object.
- `Entity.ac()` có request 5 với thời gian chờ hơn 60 giây khi nhánh render
  thiếu dữ liệu hình dáng/trang bị; không phải cơ chế tìm quái toàn map.
- Bản hiện tại đã có phạm vi chọn quái 2.000.000. Tính năng đó chỉ chọn trong
  vector entity đã nạp, không mở rộng phạm vi server gửi object.

## Phần cần xác minh trên server thật

Capture packet 4 khi vừa vào map và đứng yên; đối chiếu ID/toạ độ object ở
xa, rồi đi tới vị trí đó và so sánh các ID mới xuất hiện. Không log thông tin
đăng nhập. Nếu ID xa đã có, có thể làm danh sách/radar từ vector hiện tại.
Nếu ID chỉ đến khi đi gần, cần cơ chế server gửi toàn khu hoặc hỗ trợ request
danh sách; bytecode client riêng không chứng minh server có cơ chế đó.

Phạm vi ở đây là map/khu hiện tại, không bao gồm các khu khác của cùng map ID.

## Hướng chỉ sửa Hung_By_Son client

Kiểm tra bổ sung ngày 2026-10-05: sender và class xử lý object của bản
auto 500ms vẫn byte-identical với các dump đã kiểm. Không dùng logic server
HSO_Re_2 trong repo để kết luận khả năng của server Hung/Bạch Hổ đang chạy.

- Packet 12 không chứa bảng spawn quái sống: dữ liệu phía sau terrain được
  đưa vào các container map/trang trí/điểm chuyển map, không gọi factory quái
  hay thêm record instance vào vector entity.
- Packet 26 chỉ chứa catalog các loại quái. Có catalog không đồng nghĩa
  biết ID instance, vị trí và trạng thái sống của các con quái trong khu.
- Sender request 7 chỉ ghi một short ID, không có tham số template, vùng
  tọa độ hay wildcard/list-all trong đường gửi native này.
- Receiver packet 7 đã đọc ID, level, x/y, HP và max HP trước khi tra object.
  Nếu `(id,type=1)` chưa tồn tại hoặc đã bị xóa, nó return và bỏ chi tiết.
  Có thể hook trước guard đó để ghi một bảng thông tin riêng cho cả ID chưa
  từng xuất hiện trong packet 4. Việc này không tự tạo entity có đúng template.

Hướng khảo sát khả thi phía client là giữ nguyên luồng game, ghi tất cả
packet 4/type 1 và bắt prefix packet 7 trước guard. Nếu có các ID ứng viên
đã biết từ cùng phiên/khu, có thể hỏi lại từng ID ở nhịp có giới hạn và kiểm
tra server thật có trả chi tiết quái xa khi nhân vật đứng yên hay không.
Nếu khảo sát một khoảng ID ứng viên, phạm vi đó phải hữu hạn; không có căn
cứ từ JAR để coi ID liên tiếp hoặc mọi ID trong khoảng đều thuộc map hiện tại.
Chưa có capture hoặc thử request như vậy trong lần phân tích này.

Nếu server trả chi tiết cho ID chưa được thông báo, collector có thể thu
ID/level/tọa độ/HP mà không di chuyển. Tuy nhiên chưa đủ chứng minh danh sách
đã bao phủ toàn khu, và packet 7 không cung cấp template ID để lấy đúng tên
và hình dạng từ catalog. Không suy ra template chỉ từ level hoặc HP vì có
thể nhiều loại quái trùng các thuộc tính đó. Việc hỏi thành công ID quái xa
cũng không chứng minh server chấp nhận đánh quái xa.

Do đó chưa có bản patch được xác minh tải đủ quái toàn map ngay lần đầu vào
khu chỉ từ dữ liệu native đã đọc. Cache các ID đã gặp, mở camera hoặc tăng
phạm vi auto chỉ hỗ trợ dữ liệu đã có; không thay thế bước khám phá instance
chưa được server thông báo. Không sửa JAR phát hành hoặc gửi request mạng
trong lần khảo sát tĩnh này.

## Bằng chứng

Dump sinh từ JAR hiện tại tại `build/hung-object-load-audit-20261004/`:
`dispatch.javap.txt`, `object-packets.javap.txt`, `map.javap.txt`,
`world.javap.txt`, `entity.javap.txt`, `mob.javap.txt`, `sender.javap.txt`,
`request-callers.javap.txt`. Tài liệu tính năng chọn mục tiêu hiện hành:
[HUNG_BY_SON_BEHAVIOR.md](HUNG_BY_SON_BEHAVIOR.md#full-map-monster-targets).
