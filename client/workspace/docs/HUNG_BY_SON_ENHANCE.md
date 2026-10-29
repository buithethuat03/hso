# Hung By Son — Đập đồ chuyển từ ModUpLv

Từ bản x1 sau `C9E5073D…` (2026-10-27), tính năng **Đập đồ** của ModUpLv thay cho
Auto Cường Hóa của Hung. Bản X3 chưa cập nhật.

Nguồn: bytecode `ModUpLv.jar` (`E282B0AA…`). Tên lớp ở đây là alias trong
`build/moduplv-special-audit-20261023/classes.json`:

- `C071`: hàng chờ và các bước đập;
- `C110`: menu Đập Đồ;
- `C026`: lệnh 69 của nút trong túi đồ.

## Vì sao Hung phải về làng còn ModUpLv thì không

- Auto Cường Hóa cũ của Hung tự chặn trong client:
  - phải ở map 1, cách tọa độ (432, 528) không quá 45 theo tổng độ lệch hai trục;
  - phải mở phiên NPC pháp sư;
  - phải chờ tab cường hóa native mở rồi mới gửi lệnh.
- ModUpLv không kiểm map, vị trí hay NPC. Nó gửi thẳng ba gói opcode 67 ở bất kỳ đâu.
- Người chơi xác nhận ModUpLv đập được ở mọi nơi trên server thật. Bản Hung mới
  chưa được thử trên server thật.

## Cách dùng

1. Trong túi đồ chọn trang bị, bấm **Đập đồ**.
   - Nút này nằm đúng chỗ của **Thêm vào DS** cũ.
   - Các nút Thêm vào S1/S2/S3 vẫn ở ngay sau.
   - Nút chỉ có ở tab túi đồ, giống ModUpLv.
2. Mở **MOD HungDZ → Đập đồ >**. Dòng này nằm đúng chỗ của "Auto Cường Hóa >" cũ.
3. Bấm **Tăng cấp đập [+]** để đặt cấp muốn đạt (+1…+15).
   - Ở +0 không bật được: "Không thể đập đồ lên +0, hãy tăng cấp muốn đập!".
4. **Chọn bảo hiểm cho cấp đập >**: với mỗi cấp +1…+15, chọn một trong
   Không dùng / Đá may mắn / Cỏ ba lá / Cỏ bốn lá / Đá ba màu.
   - Bảo hiểm của "Cấp +N" dùng khi đập từ +(N−1) lên +N.
5. Bật **Auto đập đồ lên +N**.

Menu **Đập đồ** (tiêu đề `ĐẬP ĐỒ`). Mã là action của nút, gửi qua bộ phát lệnh MOD:

| Dòng | Mã |
|---|---|
| Auto đập đồ lên +N: [BẬT]/[TẮT] | -10 |
| Tăng cấp đập [+] / Giảm cấp đập [-] | -11 / -12 |
| Chọn bảo hiểm cho cấp đập > | -13 (cấp: -14 kèm cấp; loại: -15 kèm chỉ số) |
| Khoảng nghỉ: N (N×28ms) [+] / [-] | -16 / -17 |
| Xem danh sách đồ cần đập (k) > | -18 (Xóa hết danh sách: -19) |
| << Quay lại | -34 |

Nhãn dùng tiếng Việt có dấu, viết lại từ chuỗi của ModUpLv. Tất cả cài đặt mặc định
như ModUpLv và không lưu qua lần mở client:

- TẮT;
- cấp +0;
- khoảng nghỉ 30 (840ms);
- mọi cấp "Không dùng".

## Cách chạy

Mỗi khoảng nghỉ chạy một bước. Một khung của ModUpLv là 28ms, nên khoảng nghỉ 30 = 840ms
thời gian thực, không phụ thuộc tốc độ game. Mỗi bước gửi qua sender native
opcode 67 (byte, short, byte):

| Bước | Gói | Ý nghĩa |
|---|---|---|
| 1 | `(0, id trang bị, 3)` | chọn trang bị |
| 2 | `(0, id bảo hiểm, loại của món đó)` | chọn bảo hiểm cho cấp kế tiếp |
| 3 | `(2, 0, 0)` | đập bằng vàng |

Không gửi gói đồng bộ vị trí hay mở NPC.

Trước mỗi bước, client kiểm tra:

- Hàng chờ rỗng: không gửi gì, Auto vẫn BẬT.
- Dưới 6000 vàng (`player.bD`, đọc cùng gói túi đồ như ModUpLv): tắt, báo "Sắp hết vàng rồi không đập nữa đâu!".
- Món đầu hàng chờ không còn trong túi (tìm theo id + loại 3): bỏ khỏi hàng chờ.
- Cấp hiện tại ≥ cấp mục tiêu: bỏ khỏi hàng chờ. Cấp lấy từ trường cấp native,
  còn ModUpLv tách "+N" từ tên.

Món bảo hiểm được tìm theo tên trong túi. Tên được so sau khi bỏ dấu và viết thường,
vì server có thể gửi dấu dựng sẵn hoặc dấu ghép rời.

## Thông báo

Từ bản sau `BC8A9103…`, thông báo hiện giống ModUpLv. Bản `BC8A9103…` đưa mọi thông
báo lên chữ trên đầu nhân vật, nên trông như nhân vật tự chat.

ModUpLv gọi `C133.C110(text, loại)`:

- Loại 3 mở hộp thoại native có nút **Ok** (`C091.C110`). Ở Hung, hộp thoại này là
  `~liilllii.a(String)`.
- Loại 0 hiện chữ trên đầu nhân vật (`C091.C178`).

Các thông báo dùng hộp thoại Ok:

- Thêm vào danh sách: "Đã thêm X", "X đã có trong danh sách.", "Không thể đập vật phẩm này: X".
- Không bật được ở +0: "Không thể đập đồ lên +0, hãy tăng cấp muốn đập!".
- Vòng auto:
  - "Item X đã đạt cấp độ mục tiêu, bỏ qua đập!";
  - "Item không còn trong hành trang, bỏ qua!";
  - "Sắp hết vàng rồi không đập nữa đâu!".
- Các lý do dừng mà bản Hung thêm vào: thiếu bảo hiểm, server từ chối, Auto Mê cung.

Khi món cuối rời danh sách, câu "Danh sách đồ cần đập đã hết." được nối vào cùng hộp
thoại. Hai hộp thoại liên tiếp sẽ khiến hộp sau đè hộp trước.

Các thông báo vẫn hiện chữ trên đầu nhân vật, giống ModUpLv:

- bật/tắt;
- cấp đập tăng/giảm;
- khoảng nghỉ tăng/giảm;
- chọn bảo hiểm cho từng cấp;
- xóa danh sách;
- nhắc tắt Auto Mê cung khi bấm bật.

## Khác ModUpLv (đều để an toàn)

- **Mỗi lần chỉ đập một món ở đầu hàng chờ.**
  - ModUpLv cho cả hàng chờ dùng chung một bộ đếm bước.
  - Vì vậy khi có từ hai món trở lên, trong cùng một khung nó có thể chọn món A rồi
    chọn bảo hiểm theo cấp của món B.
  - Khi chỉ có một món, hai bản chạy giống hệt nhau.
- **Thiếu bảo hiểm thì tắt Auto.**
  - Báo "Hết <tên> trong hành trang!".
  - ModUpLv thì bỏ qua bước chọn và vẫn đập không bảo hiểm.
- **Bỏ chọn cỏ đang giữ.** Khi cấp kế tiếp là "Không dùng" mà server vẫn báo đang
  giữ cỏ, client gửi `(1, id cỏ, loại)` để bỏ chọn, như Auto Cường Hóa cũ của Hung.
  Nếu không, cỏ có thể bị tiêu ở cấp không cần bảo hiểm.
- **Server từ chối thì tắt.** Phản hồi opcode 67 loại 3 tắt Auto và báo nguyên văn
  câu của server. Không spam hộp thoại lỗi.
- **Khoảng nghỉ tối thiểu là 1.** ModUpLv cho giảm xuống 0, khi đó phép chia lấy dư
  ném lỗi và vòng auto ngừng mà không báo.
- **Auto Mê cung vẫn độc quyền.** Bật mê cung sẽ tắt Đập đồ. Trong lúc mê cung chạy,
  không bật lại được Đập đồ.
- **Gỡ cờ animation kết quả.**
  - Handler native bỏ qua phản hồi "đã chọn" khi cờ animation kết quả còn bật.
  - Chỉ tab cường hóa đang hiện mới chạy hết animation và hạ cờ.
  - Khi tab không hiện, client hạ cờ sau 500ms, như Auto Cường Hóa cũ khi chạy nền.

## Auto Cường Hóa cũ

Lớp `~lliillii` giữ nguyên byte. Hai lối vào của nó đã được trỏ sang module mới:

- nút "Thêm vào DS" trong menu túi đồ;
- dòng -32 trong menu MOD.

Vì vậy cờ bật của module cũ không còn được đặt. Phần hướng dẫn hiện sau đăng nhập
mô tả Đập đồ thay cho Auto Cường Hóa.

## Mã nguồn và kiểm chứng

- Module: `tools/luna/HungBySonEnhance.java.in`.
  - Biên dịch với input, lưu ở class 48 và qua bộ lọc CLDC.
  - Hàng chờ dùng list native `et`, không dùng `java.util`.
- Patch: `tools/luna/HungEnhancePortPatcher.java`.
  - Input ghim `C9E5073D…`.
  - Năm class thay đổi, thêm một class.
  - Mỗi chỗ sửa native đều kiểm bằng phép nghịch đảo chính xác.
- Build: `tools/luna/build-hung-auto-pk.ps1 -EnhancePort`. Publish: `publish-hung-auto-pk.ps1 -EnhancePort`.
- Fixture: `fixtures/hung-by-son/HungBySonEnhanceBinaryTest.java`. Fixture chạy class
  thật của candidate và bắt gói ở hàng gửi native. Nó kiểm:
  - cách nối dây bytecode;
  - thứ tự module;
  - hướng dẫn;
  - menu MOD và các menu Đập đồ bấm qua bộ phát lệnh thật;
  - nút túi đồ và lệnh 110 native;
  - ba bước gói ở map 1 xa pháp sư;
  - bảo hiểm và bỏ chọn cỏ;
  - thiếu bảo hiểm, phản hồi loại 3, thiếu vàng, đạt cấp, món mất khỏi túi;
  - mê cung và cờ animation.
- Chưa thử trên server thật. Chưa xác minh:
  - tên vật phẩm bảo hiểm thực tế trên server Hung;
  - server có chấp nhận đập từ xa với client này hay không;
  - server có giữ cỏ đã chọn qua các lần đập hay không.
