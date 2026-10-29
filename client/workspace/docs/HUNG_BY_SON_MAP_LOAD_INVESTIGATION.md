# Hung_By_Son: lỗi tải map không đủ vật thể

## Yêu cầu và trạng thái

Người dùng báo ngày 2026-10-26: vài phần trăm lần tải map bị thiếu **toàn bộ** NPC, đá dịch chuyển, bảng đổi khu và các vật thể tương tác. Nền map và một số nhân vật vẫn hiện; không sang map khác được, nhưng vẫn mở hành trang/mặc đồ được. Thoát game vào lại mới hết. Hai ảnh là bằng chứng triệu chứng, chưa có log của phiên lỗi.

Đã được cho phép tự tìm nguyên nhân, sửa và kiểm thử, phát hành bản đơn khi đạt kiểm tra. Không cần chờ xác nhận. Người dùng yêu cầu giữ hiệu suất đánh và các mode Auto PK hiện hành. Nếu phiên bị ngắt do quota, giữ ngữ cảnh để tiếp tục; người dùng đề nghị tiếp tục sau 05:00 UTC+7 nhưng phiên hiện tại không có công cụ tự hẹn chạy lại.

**Đã phát hành bản đơn sau kiểm thử.** Input là build `hung-auto-pk-radius400-20261026-a`. Hash/release/backup hiện hành ghi tại [RELEASES.md](RELEASES.md), mục “không bỏ cả gói NPC/vật thể khi đổi map”. Stage nghiệm thu: `build/hung-map-objects-20261026-b/`, `verified.json` có `Verified=true`, `Published=true`. X3 và 15 endpoint khác giữ nguyên.

## Nguyên nhân đã tái hiện trên binary

Khi nhận map mới, native đánh dấu các entity map cũ `cL=true`, trừ nhân vật chính và entity đặc biệt, nhưng giữ chúng trong vector cho tới lượt cập nhật world kế tiếp. Receiver và world update chạy trên hai luồng khác nhau. Nếu packet 4 của map mới đến trước lượt dọn và có ID/type trùng entity cũ, lookup trả về entity đang chờ xóa.

Parser packet 4 kiểm `cL`, rồi **return khỏi cả gói**, thay vì đọc tiếp record sau. Nếu record này nằm trước danh sách NPC/bảng khu/đá dịch chuyển, cả loạt phía sau không được tạo hoặc xin thông tin. Terrain đi qua packet 12 riêng nên vẫn hiện. Đây là cửa sổ phụ thuộc thời điểm cập nhật và thứ tự record, phù hợp lỗi chỉ thi thoảng gặp. Chi tiết type 2 qua request -44 chỉ được xin khi tạo placeholder mới; native không tự xin lại chỉ vì các record còn lại bị bỏ mất. Vì thế trạng thái thiếu vật thể có thể tồn tại đến khi tải lại phiên/map.

Một nhánh khác cũng làm mất đuôi gói: state 127 của người chơi chưa tồn tại tạo placeholder vào local 8, nhưng phần ghi `ea/eb` lại dùng local 7 đang null. Exception bị catch rồi return âm thầm. Fixture cũ tái hiện được cả hai đường làm mất record; không suy diễn từ build thành gameplay server thật.

## Bản sửa

- Thêm một boolean đánh dấu entity **thuộc map trước**, chỉ được đặt bởi vòng reset map native, cùng vị trí native đặt `cL`. Giữ nguyên ngoại lệ cho nhân vật chính/entity đặc biệt; không cấp phát cache hoặc quét thêm mỗi frame.
- Với dữ liệu map mới trùng entity map cũ, dùng lại factory native theo đúng loại và thông tin record. Marker này cũng được xét trong nhánh state 127 và đường nhận danh sách NPC đầy đủ (-50), để dùng đúng constructor và tránh làm hiện lại entity cũ.
- Entity bị xóa/chết **trong cùng map** vẫn theo vòng đời native; chỉ bỏ qua record đó rồi đọc tiếp. Không dùng mọi `cL` làm điều kiện hồi sinh.
- Sửa hai lệnh lấy actor ở đuôi state 127 để ghi vào placeholder vừa tạo.
- Giữ dữ liệu NPC do server cung cấp, không tự tạo NPC từ danh sách đoán, không đổi serializer hoặc thêm polling/reload vô hạn.

Chỉ thay ba class native: field entity, hàm reset world và hai hàm nhận object/NPC. Patcher gỡ ngược từng chỉnh sửa để đối chiếu toàn bộ method với input. Tất cả class combat/controller/serializer, menu, vòng 400px và 779 resource/manifest giữ nguyên.

Công cụ: `tools/luna/HungMapObjectsPatcher.java`; build/publish qua script Auto PK hiện có với `-MapObjectsFix`. Fixture: `fixtures/hung-by-son/HungBySonMapObjectLoadingTest.java`, dùng binary cũ làm đối chứng và thực thi receiver, factory, danh sách entity, request serializer thật; chỉ chặn biên queue gửi để thu payload, không kết nối tài khoản.

## Kết quả kiểm thử

- 80 kiểm tra object/NPC đạt: record map cũ ở đầu/giữa/cuối, người chơi/type 2, cùng map không hồi sinh, chỉ reset ghi marker, state 127 sống/cũ/chưa tồn tại, nhiều gói liên tiếp, phản hồi -44 ghi đúng instance mới và danh sách -50 đến trước packet 4 không tạo trùng/gửi lại -44. Input cũ tái hiện được các ca làm mất phần còn lại của gói. Log `map-objects-binary.txt` không có stack trace bất thường.
- A/B vòng đánh 787 kiểm tra trên các nhịp frame 10/16/28/40/80ms, chế độ khóa tắt/mặc định 575ms và retry đạt. Đây là kiểm cadence/request phía client, không phải phép đo trúng/phút trên server thật.
- Auto Attack native 1805, Farm Special 1417, ModUpLv 247, PK 54, menu 75, UI vòng 44+6/camera 15; hồi quy Battlefield/Maze/thiết bị/bùa và semantic/controller/route đạt.
- 282 class liên kết với `-Xverify:all`, class version giữ nguyên. 779 resource/manifest giữ byte. Khởi động MicroEmulator 12 giây với RMS bộ nhớ cô lập đạt, stderr trống. Đã backup, atomic replace và đọc lại hash; 16 endpoint khác/golden giữ nguyên.

## Bằng chứng đã có

- Bản giảm radius 400 chỉ đổi policy chọn mục tiêu và hàm vẽ vòng. So với trước Auto PK, native receiver/map/NPC/entity không đổi. Sửa constructor vật thể tĩnh ngày 24/10 vẫn còn nguyên.
- Packet nhận 12 tải terrain, décor, điểm chuyển map và trạng thái vào map. Packet nhận 4 thông báo instance; type 2 tạo placeholder rồi xin chi tiết bằng opcode gửi -44 với ID short.
- Khi xử lý map 12, native hạ cờ nhận object trước khi tải. Packet 4 bỏ qua các record nếu cờ này chưa bật. Catch của packet 12 có nhánh thoát mà không khôi phục cờ nếu lỗi xảy ra trước mốc tải phần điểm chuyển map. Đây là đường có thể giữ world chưa hoàn tất; chưa xác định được ngoại lệ thực tế nào đã xảy ra ở phiên người dùng.
- Một race có thật về mặt bytecode: singleton service dùng một field Message chung cho toàn bộ `begin -> write -> finish`. Các request đánh opcode 6/9 từ vòng cập nhật và request -44 từ receiver có thể ghi đè field này. Chưa có fixture tái hiện tại thời điểm ghi mục này.
- Không được kết luận race của một ID giải thích toàn bộ world bị kẹt. Cần kiểm chứng riêng theo chi tiết mới: **mất cả loạt vật thể**, không chỉ một vài NPC.
- Không đồng bộ cả hàm service một cách máy móc: prefix thống kê của opcode 6/9 gọi controller, trong khi vòng đánh giữ controller rồi gọi service; thêm khóa bao cả hàm có thể tạo thứ tự khóa ngược. Cần giao dịch Message cục bộ hoặc giới hạn khóa bên trong serializer, ngoài các hook controller.

## Bổ sung 2026-10-26: packet 12 dừng giữa chừng do race với vòng world

Kiểm trên `Hung_By_Son.jar` `2EB9258B…`. Packet 12 hạ cờ nhận object ở offset 30, gọi reset world ở offset 62 và chỉ bật lại cờ ở cuối hoặc trong `catch` khi đã qua offset 449 (bắt đầu nạp danh sách cổng chuyển map). Lỗi trước mốc đó giữ cờ false: packet 4 bỏ mọi record (NPC, bảng khu, đá dịch chuyển), cổng không được nạp, đến khi có packet 12 mới hoặc đăng nhập lại.

Reset (luồng receiver) duyệt vector entity bằng `et.c()`/`et.a(i)` và đặt `cL=true`. Cùng lúc vòng world (luồng game, offset 341–456) `removeElementAt` mọi entity có `cL`. Vector co lại giữa lúc kiểm và lúc lấy. Probe scratch chạy **reset native thật** song song với bản chép đúng vòng xóa đó, ép chồng lấn: 3–7% lượt reset ném `ArrayIndexOutOfBoundsException` (5–100 entity, 3.000 lượt/mức), đa số lượt còn bỏ sót entity không đánh `cL`. Tỉ lệ thực tế phụ thuộc xác suất packet 12 đến đúng lúc vòng world chạy; vòng này gọi cập nhật từng entity nên dài hơn khi đông người. Chưa đo trên server thật.

Lỗi ở offset 62 xảy ra trước khi nạp nền (offset 263) và vị trí, nên nền giữ của map trước. Khớp hoàn toàn khi đổi khu cùng map. Nếu lỗi xảy ra khi sang map khác mà nền đã là map mới thì đây không phải đường gây lỗi đó.

**Đã sửa** trong `BF1BB40C…` (`-MapResetRace`, xem [RELEASES.md](RELEASES.md)): reset duyệt bản chụp lấy dưới lock và bỏ qua ô null, `et` đọc/xóa/ghi theo index nguyên tử, vòng world xóa đúng actor đã xét. Fixture `HungBySonMapResetRaceTest`: input văng/bỏ sót, candidate 0/0.

Module Tự Áp Sát PvP (`…lilllili`, cờ `…liillililii`) chỉ chạy khi Tự Đánh PvP bật, trên luồng game. Nó không xóa entity, không ghi cờ nhận object, không gọi packet 12. Nó tìm đường bằng pathfinder native, ghi path/focus target, nên chỉ có thể làm tăng mức tiếp xúc (di chuyển liên tục ở khu PvP đông, có thể đi vào cổng). Chưa có bằng chứng nó là nguyên nhân trực tiếp.

## Phạm vi bằng chứng và việc theo dõi

Lỗi làm rơi phần còn lại của gói đã tái hiện trên input và hết trong candidate. Chưa có trace đúng phiên lỗi online của người dùng, nên chưa thể khẳng định mọi nguyên nhân có cùng biểu hiện đều đã được loại bỏ. Hai hướng phụ dưới đây **chưa phải nguyên nhân được xác nhận của phiên lỗi**, không gộp vào bản vá này:

1. Cờ tải map có thể bị giữ false nếu native gặp exception trước mốc hoàn tất. Không sửa bằng cách bật cờ giả hoặc sử dụng map thiếu dữ liệu. Receiver đã đọc toàn bộ frame trong vòng lặp, nên không có bằng chứng lỗi TCP fragmentation do một lần đọc ngắn.
2. Shared Message của singleton service là một rủi ro đồng thời riêng: 72 transaction trong service và một request map opcode 12 bên ngoài dùng `begin/write/finish` không có khóa chung. Chưa có fixture chứng minh sự cố phiên này do nó. Nếu xử lý sau, cần khóa trọn transaction hoặc dùng Message cục bộ; **không** đặt synchronized ở đầu cả hàm đánh vì prefix thống kê có thể lấy controller theo thứ tự ngược. Không tự tăng thời gian chờ đánh để che lỗi.

Nếu tái diễn trên bản đã vá, ưu tiên log theo map/khu và thứ tự opcode 12/4/-50/-44, số record/type, cờ map-ready và exception; không ghi credential hoặc payload đăng nhập. Không kết luận từ việc nền map còn hiện rằng mọi dữ liệu map đã tải xong.

Các symbol chi tiết thuộc công cụ/fixture; xem thêm `HUNG_BY_SON_OBJECT_LOADING.md` cho protocol và dump có sẵn `build/hung-object-load-audit-20261004/`.
