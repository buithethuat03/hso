# Bộ lọc nuôi pet và bán rác — Hung By Son x1

## Cách dùng

Trong menu Auto nuôi pet, dòng `Ăn trang bị thuộc loại >` mở bốn lựa chọn độc lập:

| Lựa chọn | Trang bị |
| --- | --- |
| Sức mạnh | Vũ khí chiến binh và sát thủ |
| Khéo léo | Nhẫn và dây chuyền |
| Thể lực | Nón/mũ, quần/váy, áo, găng tay, giày |
| Tinh thần | Vũ khí xạ thủ và pháp sư |

Dòng có `[x]` được chọn. Có thể chọn nhiều nhóm; bỏ chọn cả bốn thì không ăn trang bị. Bộ lọc loại kết hợp với điều kiện phẩm chất/cường hóa có sẵn của nuôi pet; chế độ ăn kẹo vẫn theo lựa chọn riêng.

Trong Auto bán rác có thêm `Chỉ bán đồ khóa [ ]` và `Bán rác thuộc loại >`. Bốn nhóm có cùng ý nghĩa trên, lưu riêng với nuôi pet. Khi chọn chỉ bán đồ khóa, vật phẩm phải có byte khóa dương do server cung cấp, cùng cờ mà client dùng vẽ ảnh ổ khóa. Không dựa vào màu tên hoặc dò chuỗi `[Khóa]`.

Một vật phẩm chỉ được bán khi **đồng thời** vượt qua phẩm chất, trạng thái khóa và nhóm loại đã chọn. Vẫn giữ quy tắc gốc chỉ xét trang bị chưa cường hóa. Ví dụ chọn mức đồ tím, chỉ bán khóa và nhóm sức mạnh thì bán trang bị tím trở xuống, có khóa, thuộc vũ khí chiến binh/sát thủ, chưa cường hóa.

Mặc định bốn nhóm của mỗi tính năng đều bật; chỉ bán đồ khóa tắt. Lựa chọn lưu trong MIDP RMS `hbs_equipment_filters_v1`, không ghi đè bản ghi cấu hình gốc. Cánh, huy chương và các loại ngoài bốn nhóm người dùng yêu cầu không khớp bộ lọc.

## Khi nào bán rác được kích hoạt

Luồng gốc yêu cầu Farm chính và Auto bán rác đang bật, cùng một chế độ Farm đánh/chạy đang hoạt động. Tại trạng thái bán rác đang nghỉ, không bị các điều kiện chặn hoặc thời gian chờ, nó kích hoạt khi:

```text
số món trong túi >= sức chứa túi - 3
```

Sức chứa đọc từ dữ liệu nhân vật; luồng gốc dùng 30 nếu chưa có giá trị dương. Đây là kiểm tra túi gần đầy, không đợi hết sạch ô trống.

Trước khi tạm dừng Farm hoặc đi về làng, bản mới quét túi bằng đúng điều kiện bán. Nếu không có món phù hợp, Auto bán rác tự tắt, lưu trạng thái và báo `Túi gần đầy nhưng không có đồ phù hợp bộ lọc. Đã tắt Auto bán rác.`. Farm giữ nguyên, không có chuyến đi vô ích và không lặp việc kích hoạt lại.

Nếu cấu hình hoặc hành trang thay đổi trong một chuyến bán đang chạy, khi quét hết túi không còn món hợp lệ mà túi vẫn gần đầy, bản mới đánh dấu dừng. Nó để luồng gốc hoàn tất việc thu dọn/trở về/khôi phục Farm rồi mới tắt Auto bán rác. Điều này tránh để chế độ đi bán hoặc Farm bị treo giữa chừng. Nếu đã giải phóng đủ ô túi thì giữ auto bán rác bật cho lượt sau.

## Ưu tiên Bùa về làng khi bắt đầu chuyến bán

Khi túi đạt ngưỡng còn tối đa ba ô và có trang bị hợp lệ cần bán, client lưu map xuất phát/tạm dừng Farm như luồng gốc, rồi tìm Bùa về làng trong hành trang. Nhận diện giống nhánh Auto Farm không đánh quái: tên vật phẩm chứa đúng chuỗi `Bùa về làng` mà helper Farm đang dùng, rồi gửi ID thực của món tìm được. Không chốt ID 142, nhóm vật phẩm 7 hoặc một trường số lượng riêng. Không tìm trong rương hoặc đồ đang mặc.

**Sửa nhận diện ngày 2026-10-25:** bản `2F07C918…` dùng các ràng buộc ID/nhóm/số lượng kể trên nên khác Auto Farm. Kiểm tra trên cùng đối tượng vật phẩm cho thấy Farm gửi dùng bùa trong khi bộ tìm của bán rác trả về rỗng. Bản sửa đọc/giải mã marker trực tiếp từ bytecode helper Farm khi build; chọn cùng món và dùng cùng service với ID thực. Không giả định ID từ một tính năng khác hoặc từ fixture dựng tay.

Nếu có bùa và nhân vật đang sống ở ngoài các làng bán hàng, client dùng bùa một lần bằng lệnh Sử dụng native, tạm dừng tìm đường trong lúc chờ server đổi map. Tới làng thì tiếp tục bước đi đến NPC/bán đồ của luồng gốc. Nếu không có bùa thì giữ nguyên lựa chọn làng gần nhất và nhánh di chuyển cũ. Đang ở làng thì không tiêu hao bùa.

Mỗi chuyến chỉ dùng một bùa. Sau 10 giây chưa về làng, hoặc lệnh dùng bùa gặp lỗi, client khôi phục điểm đến ban đầu và tiếp tục đi theo nhánh cũ. Tắt Auto bán rác hoặc đổi trạng thái bán sẽ hủy việc chờ. Tắt Farm chính thì tạm dừng chuyến; bật lại tiếp tục xử lý map đến hoặc nhánh dự phòng, không dùng thêm bùa. Map xuất phát vẫn được giữ để sau khi bán quay lại Farm như trước. Nếu không có món phù hợp bộ lọc thì tắt bán rác trước bước này, không dùng bùa.

Trạng thái chờ thuộc `SaleReturnScrollController`; `HungSaleReturnScrollPatcher` sinh bridge và thêm hai hook vào module bán rác. Gỡ hai hook cho lại đúng bytecode update ban đầu. Bản sửa nhận diện tiếp theo chỉ thay `findScroll` trong bridge bằng `HungFarmScrollMatchFixPatcher`; mọi class native, serializer, menu, RMS, bộ lọc loại/khóa/phẩm chất, resource và các method khác giữ nguyên.

Build hiện hành: `build-hung-equipment-filters.ps1 -FarmScrollMatch -BuildTag hung-sale-scroll-farm-match-20261025-a`, đầu vào pin `2F07C918…`. Stage chứa 44 kiểm tra controller và 76 kiểm tra trên JAR. Fixture chạy chính helper dùng bùa của Auto Farm và update bán rác trên cùng vật phẩm với nhiều ID/nhóm/metadata khác nhau, đối chiếu gói native opcode 32 và short ID tại hàng đợi giả; giữ các kiểm tra một lần/chuyến, chờ map, map Farm gốc, dự phòng, bốn ô trống, không có đồ bán, đã ở làng và tạm dừng-tiếp tục Farm. Bản cũ thất bại ở đối chiếu cùng món với Farm; bản sửa đạt. Không kết nối server trong fixture.

## Nguồn và kiểm tra

Chính sách nằm ở `src/luna/features/EquipmentFilterPolicy.java`, cấu hình ở `EquipmentFilterSettings.java`; quyền truy cập binary/menu/hook chỉ ở bridge sinh bởi `tools/luna/HungEquipmentFiltersPatcher.java`. Không biên dịch lại support legacy và không thay serializer mạng. Patcher pin SHA-256 input, kiểm đúng owner/member/descriptor, kiểm các action vừa với byte và không trùng, giữ toàn bộ resource và các method native ngoài allow-list.

Ánh xạ mã loại được đối chiếu từ bảng loại → ô mặc đồ và từng frame 20×20 đã phóng lớn của ảnh native `x1/interface/screentab6.png`. Không suy ra loại bằng tên vật phẩm, các dòng cộng chỉ số hoặc trường giới hạn nghề, vì đồ dùng chung có thể mang mã nghề ngoài 0–3.

| Nhóm | Mã loại native | Ô đồ |
| --- | --- | --- |
| Sức mạnh | 8, 9 | Vũ khí chiến binh, sát thủ |
| Khéo léo | 4, 5 | Nhẫn, dây chuyền |
| Thể lực | 0, 1, 2, 3, 6 | Áo, quần/váy, mũ, găng, giày |
| Tinh thần | 10, 11 | Vũ khí xạ thủ, pháp sư |

**Sửa ngày 2026-10-25:** bản `92EF9789…` đã đọc nhầm hai icon nhỏ, gán nhầm mã 5/6. Thực tế mã 6 ánh xạ ô mặc số 8 là giày, mã 5 ánh xạ ô số 4 là dây chuyền. Điều này làm giày bị bỏ qua khi chỉ chọn thể lực và dây chuyền bị chọn nhầm. Bản sửa chỉ đổi phương thức bảng nhóm; toàn bộ class native, menu, RMS và resource giữ nguyên. Mọi mã 0–6 thuộc đúng một trong hai nhóm giáp/phụ kiện, bốn mã vũ khí 8–11 được đối chiếu thêm với nghề native. Mã 7 là cánh, ngoài phạm vi yêu cầu.

Output hiện hành ở `build/hung-sale-scroll-farm-match-20261025-a/`; hash/trạng thái phát hành tại [RELEASES.md](RELEASES.md). Bản thêm nhánh bùa trước sửa nhận diện ở `build/hung-sale-scroll-20261025-b/`. Bản sửa loại ô đồ trước đó được build bằng `tools/luna/build-hung-equipment-filters.ps1 -SlotFix -BuildTag hung-equipment-slots-20261025-a`, patcher `HungEquipmentSlotFixPatcher`. Build gốc trước sửa ở `build/hung-equipment-filters-20261025-c/`.

Đã đạt 47.118 kiểm tra chính sách, 237 kiểm tra trên binary candidate, 23 kiểm tra giả lập menu/callback/RMS/vẽ giao diện; kiểm semantic/controller/route, hồi quy Auto đánh/Farm/phó bản/mê cung/đi buôn/cường hóa, liên kết 278 class, CLDC/version/resource checks và startup giả lập. Kiểm binary quét từng loại 0–11 qua cả bốn nhóm và chạy chính vòng chọn món bán native với túi `[dây chuyền, giày]`: chọn thể lực chỉ phát yêu cầu bán giày vào bộ nhận giả, không gửi mạng. Fixture này thất bại trên bản cũ và đạt trên bản sửa. Chưa thử gửi lệnh ăn/bán trên server thật. Chỉ cập nhật x1; X3 và endpoint khác giữ nguyên.
