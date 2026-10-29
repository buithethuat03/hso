# Hung By Son: đối chiếu ba cơ chế với Bạch Hổ

Phạm vi là tốc độ game, chọn/đánh quái trên map đang tải và đường dùng chiêu.
Bản tham chiếu là `bachho_final.jar` được ghi trong release record; không lấy
thông số từ patch FPS hoặc cast 300 ms cũ làm chuẩn.

## Tốc độ game

Menu `Tốc độ Game: [xN]` mở danh sách lựa chọn và đánh dấu mục hiện tại.
Bạch Hổ dùng x1–x5; theo yêu cầu bổ sung, bản sửa Hung dùng x1, x2, x3,
x0.8, x0.9. Mặc định khi khởi động là x1; không lưu giá trị này vào RMS.
Chọn một giá trị đặt trực tiếp hệ số, không lần lượt đổi FPS.

Mã nút native chỉ rộng một byte có dấu. Dùng mã 101–105 để không bị tràn
thành mã nhập key; fixture chạy chính `bt` event/callback để kiểm. Hệ số
0.8/0.9 tính bằng 8/10, 9/10 với phần dư tích lũy, không làm chậm đồng hồ
thực dùng cho request/cooldown. Trạng thái thay JAR xem release record.

Vòng game nhân phần thời gian đã trôi qua với hệ số khi lớn hơn 1 rồi cộng
hai lần vào bộ tích lũy, dùng bước 40 ms và giới hạn ba lần cập nhật mỗi lượt.
Phần dư từ 120 ms trở lên được xóa. Đồng hồ request được cập nhật sau mỗi
tick; nhịp dịch chuyển là 5/8 tick như bản mẫu. Sau cập nhật, game đo thời
gian paint và đợi `max(1, 16 - paintCost)` ms. Cơ chế bù phần dư FPS và chặn
riêng bước dịch chuyển của nhân vật trong bản Hung trước đó được loại bỏ.

## Mục tiêu trên map

Bản mẫu dùng phạm vi 2.000.000 cho tìm và giữ mục tiêu; chọn mục tiêu gần
nhất trong danh sách đối tượng hiện tại, theo thứ tự duyệt của bản mẫu.
Trong vòng đánh dấu nhiều mục tiêu, khoảng cách được thay bằng 0 trước
phép so sánh tầm đánh. Các bộ lọc đối tượng hợp lệ vẫn áp dụng.

Đường bắt đầu cast đi thẳng vào bước chuẩn bị có sẵn, không đi qua nhánh
tính khoảng cách và lập đường đi đến mục tiêu.

## Dùng chiêu

Cổng auto dùng phép so sánh nghiêm ngặt `elapsed > 400 ms`. Cổng request
dùng `elapsed > nativeInterval + 300 ms`, đúng bytecode hiện hành của Bạch Hổ.
Hai cổng này không đồng nghĩa mọi skill đều được server chấp nhận đúng mỗi
400 ms: cooldown riêng, mana và phản hồi của server vẫn là các điều kiện
có sẵn trong bản mẫu.

Sau bước chuẩn bị actor, đường cast gọi sender trực tiếp. Không thêm
override gửi ở frame 1 hoặc hồi phục ở frame 2 của thử nghiệm 300 ms cũ.
Phần dựng animation và ngưỡng hồi phục native được giữ như đường tương ứng
đã đối chiếu với bản mẫu.

## Build và bằng chứng

Patcher và fixture nằm trong `tools/luna/` và `fixtures/hung-by-son/`;
các mapping nằm trong `symbols/`. Pipeline staging:

```powershell
.\tools\luna\build-hung-bachho-parity.ps1 `
  -InputJar ..\jar\backup\Hung_By_Son-before-bachho-parity-20261004.jar `
  -ReferenceJar ..\jar\bachho_final.jar `
  -DependencyDir C:\path\to\hso-deps
```

Script chỉ sinh và kiểm JAR mới. Nó không thay release hoặc dọn endpoint
khác. Hash, output, backup, kết quả kiểm và giới hạn xác minh được ghi một
nơi tại [RELEASES.md](RELEASES.md). Kiểm binary/host và khởi động giả lập
không thay thế việc quan sát game trên server thật.
