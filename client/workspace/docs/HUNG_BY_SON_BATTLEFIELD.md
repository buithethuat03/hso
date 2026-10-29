# Hung By Son — Mode chiến trường

Mở **Menu → Auto đánh > → Mode chiến trường** để bật/tắt. Mặc định luôn
tắt khi mở game; lựa chọn này không ghi RMS và không tự bật Auto đánh.

Khi bật, vật thể khác dùng ngay vị trí mới nhất server đã gửi. Client giữ
tọa độ server riêng và áp dụng trước/sau cập nhật của cả lớp entity chung
lẫn các lớp kế thừa, tránh các nhánh người chơi/quái bỏ qua cập nhật chung.
Nhân vật của mình vẫn di chuyển theo cơ chế native. Đây là vị trí server
đã nhận được, không phải dự đoán vị trí khi server chưa gửi dữ liệu.

Mode giữ tối đa **10 tên người chơi khác nhau** bị mình kết liễu. Chỉ thêm
khi packet kết quả đánh người có ID người đánh trùng nhân vật của mình,
sát thương dương, mục tiêu đang có HP dương trước packet và HP sau đòn bằng
0. Không ghi nhận từ việc chọn mục tiêu, gửi đòn hoặc chỉ gây sát thương.
Mục tiêu phải là người chơi đã biết tên và level; bỏ qua level 40, quái,
bản thân và tên `Xà nữ` (không phân biệt hoa/thường, bỏ khoảng trắng hai đầu).
Packet bị cắt hoặc sai cấu trúc không được ghi nhận một phần; luồng đọc
được reset để receiver native tiếp tục xử lý.

Danh sách giữ qua đổi map/khu. Nếu tên đã có trong danh sách bị kết liễu
lại, thứ tự cũ được giữ nguyên. Khi có tên thứ 11, tên đầu tiên bị đẩy ra.
Tắt mode hoặc đóng game xóa danh sách. Dữ liệu riêng cho từng client X3.

Trong **Auto quái + PK**, người có tên trong danh sách bị loại ở cả bước
chọn mục tiêu và kiểm tra request đang chờ. Tên trên đầu nhân vật dùng
bitmap font **đỏ in đậm** sẵn có của client. Tắt mode trả về màu và bộ lọc
native; đánh tay vẫn theo game.

## Loại vật thể khỏi cache xác quái

Mọi map đều bỏ cache template có tên `Xà nữ`, `Lửa thiêng` hoặc `con Ma`,
kể cả khi mode đang tắt (bỏ khoảng trắng hai đầu, không phân biệt hoa/thường).
`Lửa thiêng` và `con Ma` là vật thể, không lưu vào cache xác quái.
Hai tên này cũng không xuất hiện trong **Chọn quái**, dù lấy từ vật thể đang
hiện, cache nhớ quái hay lựa chọn đã lưu. Lựa chọn cũ của hai vật thể bị bỏ qua;
nếu chỉ từng chọn chúng thì trở về Tất cả quái. `Xà nữ` vẫn có thể chọn trong menu.
Record cũ bị loại khi nạp dữ liệu; nếu catalog tên chưa về, client kiểm
lại trước khôi phục/xác nhận và quét tối đa tám record đang hoạt động mỗi
tick. Khi nhận ra template bị loại, nó dọn các map/khu cùng server và ghi
lại cache, đồng thời gỡ placeholder do cache sở hữu. Không xóa quái native
đang được server thông báo. Template ID trùng ở server khác được giữ lại.
Quy tắc map 48 không dùng cache vẫn giữ nguyên.

## Màu đường kẻ focus (2026-10-28)

Khi bật Mode chiến trường và chọn Auto PK, đường tới người chơi đang focus
có màu cam `0xFFA500`, kể cả level 40. Đánh thường hoặc focus tay dùng mục tiêu
native; khi Auto đang chạy vòng Tấn công đặc biệt, dùng mục tiêu khóa của vòng
đánh đó. Xà nữ vẫn đỏ. Người khác giữ màu xanh da trời (level 40) hoặc trắng;
tắt chiến trường thì giữ màu trắng như trước. Bộ lọc người chết, ra ngoài vòng,
người không thể PK và FIFO chiến trường vẫn áp dụng trước khi vẽ.

## Build và xác minh

- X1: `tools/luna/build-hung-battlefield.ps1` ghim JAR nguồn, kiểm symbol,
  đảo ngược các hook để đối chiếu nguyên vẹn method cũ, kiểm resource và
  manifest, compile Java 7 và sàng lọc API CLDC.
- X3: `tools/luna/build-hung-battlefield-x3.ps1` lấy đúng candidate X1 đã
  kiểm tra, giữ launcher và EXP backend chung, kiểm liên kết và ba namespace.
- Publish: `tools/luna/publish-hung-battlefield.ps1` yêu cầu hai hồ sơ đạt,
  kiểm feature trên ba slot, backup/đối chiếu hash trước thay đúng hai JAR.

Các fixture dùng packet tổng hợp và runtime native trong môi trường cách
ly. Chưa xác minh gameplay hoặc cách server tính điểm chiến trường trên
phiên chơi thật. Hash phát hành, backup và kết quả thực tế nằm trong
[RELEASES.md](RELEASES.md).

## Vật thể tĩnh (2026-10-24)

Chỉ vật thể đã nhận tọa độ từ gói server (người chơi, quái) mới được căn theo vị trí
server. NPC như bảng đổi khu "Khu", vật phẩm rơi và các vật thể tĩnh khác giữ vị trí
native khi bật chế độ; trước đây constructor chung đánh dấu (0,0) là vị trí đã biết
nên chúng bị kéo về góc map và biến mất. Chi tiết: [RELEASES.md](RELEASES.md).
