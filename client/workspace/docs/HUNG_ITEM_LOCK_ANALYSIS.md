# Nhận biết đồ khóa trong Hung_By_Son.jar

Kiểm tra bytecode ngày 2026-10-25, trực tiếp trên `client/jar/Hung_By_Son.jar`.
SHA-256: `F07535B4B0A7B71A896F9F5DBE9C9871C846B92827EA0273910CEC704EBBF600`.

## Kết luận

Client có **cờ khóa riêng dạng byte** trên đối tượng vật phẩm. Điều kiện hiển thị ổ khóa là `lockFlag > 0`. Không cần nhận diện ảnh hoặc dựa vào màu tên đồ.

Tên symbol bên dưới rút gọn dãy ký tự `i` đầu tên thành `~`, theo hàm `shortName` trong `tools/luna/ItemLockInspect.java`. Đây là ký hiệu tra cứu, không phải tên có thể dùng nguyên văn để patch bytecode.

| Vai trò | Symbol rút gọn | Kiểu |
| --- | --- | --- |
| Lớp vật phẩm cơ sở | `~llilli` | class |
| Lớp vật phẩm trang bị | `~lililiii` | class |
| Cờ khóa | `~llilli.~liiiliiii` | `B` (byte) |
| Ảnh ổ khóa | `~liiiiil.aw` | `L~lilli;` |
| Tên vật phẩm hiển thị | `~llilli.~liiiiliii` | `Ljava/lang/String;` |
| Tên trước khi thêm cấp cường hóa | `~llilli.~llllilli` | `Ljava/lang/String;` |

## Bằng chứng

1. Hàm nạp giao diện `~lili.a()V` nạp `/interface/lock.img` rồi gán vào `~liiiiil.aw`.
2. Cả ba đường vẽ ô vật phẩm trong lớp `~llilli` đều đọc `~liiiliiii`, dùng `IFLE` để bỏ qua khi giá trị không dương, và vẽ `aw` tại `(x + 4, y + 3)` khi giá trị dương.
3. Constructor trang bị nhận byte này từ tham số thứ 19 rồi gán vào trường trên. Luồng đọc hành trang `~lllllll.~(L~lllllil;Let;B)V`, nhánh loại vật phẩm `3`, đọc nó bằng `DataInputStream.readByte()` vào local 25 rồi truyền vào constructor. Vì vậy đây là dữ liệu nhận từ server.
4. Constructor vật phẩm cơ sở khởi tạo cờ bằng `0`; đường sao chép vật phẩm giữ nguyên cờ. Các tham chiếu đã kiểm tra không có thao tác client tự bật/tắt cờ ngoài khởi tạo và sao chép.

Logic tương đương, dùng tên dễ hiểu:

```java
boolean isLocked = item.lockFlag > 0;
if (isLocked) {
    draw(lockImage, x + 4, y + 3);
}
```

`lockFlag` ở đây là tên diễn giải cho trường byte đã xác định, không phải field có sẵn mang tên này. Trên đối tượng đã nhận đủ dữ liệu, `0` không vẽ khóa; mọi giá trị `<= 0` đều đi nhánh không vẽ. Không nên suy ra vật phẩm chưa tải xong là đồ không khóa chỉ từ giá trị mặc định.

## Nhãn [Khóa] trên tiêu đề

Trong luồng hành trang trang bị, tên được đọc bằng `readUTF()`, truyền vào constructor và lưu vào hai trường tên. Client có thêm phần ` +<cấp cường hóa>` vào tên hiển thị. Tab đồ sao chép tên hiển thị vào tiêu đề bảng thông tin.

Không tìm thấy literal `[Khóa]` hoặc đoạn ghép nhãn này trong các class của JAR. Luồng bytecode phù hợp với việc server gửi sẵn nhãn trong tên. Cờ vẽ ổ khóa và chuỗi tên là hai dữ liệu riêng; chưa có packet thực tế từ hai vật phẩm trong ảnh để đối chiếu giá trị cụ thể.

Literal `Khóa` trong bảng ngôn ngữ được dùng làm nút khóa giao dịch, không phải bằng chứng cho trạng thái khóa vật phẩm. Chuỗi `Khóa ... ms` trong `SkillCastStats` cũng thuộc tính năng khác.

## Phạm vi xác minh

Đã kiểm tra bytecode và tham chiếu field/resource trực tiếp trên JAR nêu trên, có kiểm tra độc lập đường nạp/vẽ ảnh khóa. Chưa chạy emulator hoặc bắt packet server. Không thay đổi JAR.
