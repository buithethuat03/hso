# Hung By Son x1/X3 — Auto thay đồ khi BUFF

Menu mod → Cài đặt cấu hình → Auto Tự Đánh → **Auto thay đồ khi BUFF >** (dòng ngay trước
"<< Quay Lại", action −107). Không lưu RMS: mỗi lần mở game là TẮT, chưa chọn chiêu nào.
X3 có cùng tính năng ở cả ba client, mỗi client bật/tắt, chọn chiêu và theo dõi hồi chiêu riêng.

## Menu

Trang **AUTO THAY ĐỒ KHI BUFF**:

1. **Auto thay đồ: [TẮT]/[BẬT]**. Khi bật mà công tắc Thay Đồ đang TẮT hoặc chưa chọn chiêu,
   hộp thoại Ok báo còn thiếu gì; tính năng vẫn bật.
2. Một dòng cho mỗi chiêu buff đã học (template loại 1, cấp học > 0): `Tên Lv<n>` với
   n = cấp học + cấp cộng thêm (như tab Kỹ năng), thêm `[x]` khi được chọn. Bấm để chọn/bỏ.
   Chưa học chiêu buff nào thì hiện "Chưa học chiêu buff nào".
3. **<< Quay Lại** về Tùy chọn tự đánh.

Mọi dòng dùng chung action −107, phân biệt bằng byte `extra` (0 mở trang, 1 công tắc, 2 quay lại,
10 + ID chiêu). Rà soát 2026-10-27: −107/−108/−109 chỉ xuất hiện trong switch opcode gói tin của
`~liiil.run`, không phải action menu; −115 là Mode chiến trường (hằng nạp bằng `ldc`).

## Chuẩn bị

- Chọn món → **Thêm vào S1** (đồ mặc lúc buff, ví dụ dây chuyền cộng Kỹ năng phòng thủ) và
  **Thêm vào S2** (đồ mặc lại sau buff).
- Menu → **Thay Đồ: [BẬT]**: module Thay Đồ chỉ mặc đồ khi công tắc của nó BẬT.
- Bật **Auto Farm** hoặc **Tấn công đặc biệt**. Ngoài hai chế độ này tính năng không làm gì.

## Hành vi

Chỉ chạy khi world sẵn sàng (`farmWorldReady()`) và nhân vật còn sống. Mỗi chiêu được chọn chạy
một chu kỳ riêng:

1. Hồi chiêu còn ≤ 2000 ms (`~liil.b`, client tính lại mỗi lần cập nhật nhân vật): chiêu vào
   trạng thái chờ. Có chiêu chờ mà chưa mặc Set 1 → gửi **Mặc Set 1** (action −24) qua bộ điều
   phối MOD, đúng như bấm menu ("Đang mặc Set 1...").
2. Chiêu buff vẫn do **Farm / Tấn công đặc biệt** tung (vòng đặc biệt gửi yêu cầu buff opcode 40).
3. **Thành công:** gói trả lời buff **opcode 40** của server có caster là nhân vật mình và đúng
   chiêu đó. Đây cũng là gói mà `HungBySonSkillStats.buff` tính là lần "trúng" của buff ở bảng
   Chi tiết tung chiêu. Một câu trả lời đến trước khi chiêu vào trạng thái chờ không được tính.
4. Không còn chiêu nào chờ → gửi **Mặc Set 2** (action −25). Đang mặc dở một set thì lệnh kế tiếp
   đợi module Thay Đồ mặc xong.

Nhiều chiêu được chọn: chiêu nào tới 2 s thì vào chờ; Set 1 được giữ tới khi mọi chiêu đang chờ đều
xong, nên hai chiêu chồng thời gian không làm đổi qua lại Set 1/Set 2.

**Bỏ cuộc:** chiêu đã hết hồi 5 s mà chưa ai tung (thiếu MP, Farm đang đi đường…), hoặc đã tung mà
2 s không có trả lời opcode 40 → mặc lại Set 2, chữ trên đầu báo "Chưa buff được <tên>: mặc lại
Set 2", chiêu đó đợi 10 s mới được vào chờ lại.

**Dừng:** tắt tính năng, tắt cả Farm lẫn Tấn công đặc biệt, hoặc rời trạng thái hợp lệ khi Set 1
đang mặc → mặc lại Set 2 ngay khi được (Thay Đồ BẬT, world sẵn sàng, còn sống).

**Set trống:** module Thay Đồ không cho đọc danh sách set (kiểu `Vector`, bị kiểm CLDC của pipeline
cấm). Sau lệnh mặc, nếu cờ "đang mặc" của Thay Đồ không bật thì set đó trống: tính năng TẮT, hộp
thoại Ok "Set n trống! Hãy chọn đồ > Thêm vào S1/S2. Đã tắt Auto thay đồ khi buff".

## Source và build

- Module: `tools/luna/HungBySonBuffSwap.java.in` (marker `@~suffix@`, biên dịch `--release 7`,
  lưu class 48).
- Patcher: `tools/luna/HungBuffSwapPatcher.java`, input ghim `14E2AD66…`. Thêm `HungBySonBuffSwap`;
  sửa ba class, mỗi chỗ có kiểm nghịch đảo: Tùy chọn tự đánh `~lliiiiil.~lilllili` gọi
  `addAttackRows` trước dòng "<< Quay Lại" (−39), handler opcode 40 `~lllllll.~liilll` gọi
  `onBuff(stream)` ngay sau `HungBySonSkillStats.buff`, loader `register()` giữa Stamina và ModUpLv.
- Build/publish: `build-hung-auto-pk.ps1 -BuffSwap`, `publish-hung-auto-pk.ps1 -BuffSwap`.
- Fixture: `fixtures/hung-by-son/HungBySonBuffSwapBinaryTest.java` chạy class thật: menu qua bộ
  điều phối native, module Thay Đồ thật (gói trang bị opcode 11 bắt ở hàng đợi gửi), vòng Tấn công
  đặc biệt thật gửi yêu cầu buff opcode 40, câu trả lời opcode 40 dựng sẵn qua handler native.

## Chưa xác minh trên server thật

- Server có áp cấp chiêu cộng thêm của Set 1 vào lần buff hay không, và hiệu ứng buff còn giữ sau khi
  mặc lại Set 2 hay không.
- Thời gian mặc một set (mỗi món một gói opcode 11, cách nhau vài khung) có kịp trong 2 s.
- Server có trả opcode 40 cho mọi lần buff thành công của Farm (đường cast native) như vòng đặc biệt.

Hash, backup và kết quả kiểm: [RELEASES.md](RELEASES.md).
