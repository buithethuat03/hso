# Hung By Son x1 và X3 — thông tin EXP

Nút `i` nằm dưới nút `A`, cạnh dòng EXP của HUD. Chạm và thả trong nút để
mở bảng; kéo ra ngoài hủy thao tác. Bảng dùng hộp thông báo native với nút
`Xác nhận` để đóng. Enter/fire/phím mềm không đóng bảng. Auto Farm cũng
không xóa bảng EXP khi đang mở; quy tắc này chỉ áp dụng cho đúng bảng EXP
của nhân vật đang chơi, không giữ lại hộp cũ qua logout/đổi màn hình.

Font, dấu `+`, dấu phẩy, khung, thanh XP và dòng EXP K/M có sẵn giữ nguyên.
Từ bản rolling/fine 2026-10-28, tử số dòng EXP K/M được tính từ cùng phần trăm
nội suy ba chữ số đang hiện, có cập nhật ngay trong cùng bước 0,1% native.
Dạng K/M vẫn làm tròn một chữ số; trường hợp chưa có pha fine dùng phần trăm native.
Icon dùng đúng SVG `icon-info (1).svg` 12×12 do người dùng cung cấp, chuyển
sang PNG 12×12 để chạy trong J2ME; bằng đúng kích thước ảnh `A`. Nó nằm
thẳng trục giữa của nút `A`: tâm x103 ở layout thường, x91 ở layout gọn;
không cộng thêm origin của panel. Ảnh được nạp/cache một lần và scale bằng
nearest-neighbor nếu graphics scale thay đổi. Cả ba client X3 dùng cùng ảnh
PNG; trạng thái nút, bảng thông báo và tốc độ EXP riêng cho từng client.

Riêng `Hung_By_Son_copy_special_skill.jar`: bảng có thêm tab "Chi tiết tung chiêu"; xem
[tính năng từ ModUpLv](HUNG_BY_SON_MODUPLV_PORT.md).

## Các giá trị trong bảng

- **Kinh nghiệm/giờ (ước tính):** từ bản economy 2026-10-29, chờ đủ **5 phút**
  từ lần nhận EXP dương đầu tiên. Từ phút 5 đến phút 15, dùng tổng EXP dương
  đã nhận chia thời gian thực đã đo; sau phút 15 chỉ giữ **15 phút gần nhất**.
  Công thức `EXP trong cửa sổ × 3600000 / thời gian cửa sổ (ms)`, làm tròn
  số nguyên và ngăn hàng nghìn bằng dấu chấm. Nghỉ đủ 15 phút hiện `0`;
  nhận EXP lại tiếp tục cửa sổ hiện tại mà không phải chờ năm phút lần nữa.
  Bộ đếm dùng 900 ô một giây (giây hiện tại và 899 giây trước), khoảng 7,2KB
  dữ liệu mỗi client. Ranh giới loại packet có độ phân giải một giây, có thể
  loại sớm dưới một giây so với cửa sổ chính xác theo mili giây. Thời gian là
  đồng hồ thực; tốc độ game không nhân tốc độ EXP.
- **Kinh nghiệm lên cấp (ước tính):** tổng EXP của cấp hiện tại, cùng dữ
  liệu mẫu số đang hiện ngoài HUD, làm tròn tới số nguyên, ngăn hàng nghìn
  bằng dấu chấm và không viết tắt K/M. Chỉ hiện khi HUD đã có tổng EXP hợp lệ. Đây là tổng từ 0% tới
  100% của cấp, không phải riêng phần còn thiếu.
- **Thời gian còn lại (ước tính):** chỉ hiện khi có cả tốc độ dương và
  tổng EXP cấp. Dùng phần EXP còn thiếu của cấp hiện tại chia tốc độ,
  tận dụng phần trăm ba chữ số nếu có, nếu không dùng phần trăm native.
  Định dạng `02 giờ 05 phút`; số phút làm tròn lên, số giờ được dài hơn
  hai chữ số khi cần.

Từng mục thiếu dữ liệu hiện đúng `Đang tính toán...`. Dòng chờ ngoài HUD
cũng dùng chuỗi này thay `Caculating...`.

Tốc độ theo phiên nhân vật/server, không lưu RMS hay nối thời gian offline.
Lên cấp hoặc reset đoạn lấy mẫu tổng EXP không xóa tốc độ đang đo. Packet
vượt cấp vẫn được tính một lần vào EXP đã nhận, dù không dùng để khớp tổng
của một cấp. EXP âm không cộng vào tổng EXP dương đã nhận. Snapshot nhân
vật/server khác, ngắt kết nối, đồng hồ lùi hoặc tràn tổng bắt đầu phép đo
mới. Dữ liệu tổng EXP theo level vẫn dùng RMS và quy tắc học hiện có.

Thời gian còn lại dùng tốc độ cửa sổ mới. Khi EXP/giờ bằng 0, mục thời gian
còn lại hiện `Đang tính toán...`. Nội dung tab EXP và vàng tự làm mới mỗi
giây khi đang mở, đồng thời cập nhật ngay khi chuyển tab.

## Tab Vàng / phút và hành trang

Tab thứ tư có tên đúng `Vàng / phút`, gồm **Vàng hiện tại**, **Vàng/giờ
(ước tính)** và **Vàng đã nhận từ khi đăng nhập**. Tốc độ dùng cùng cửa sổ
5–15 phút như EXP, với mốc bắt đầu là lần nhận vàng dương đầu tiên.
Số dư đầu tiên chỉ tạo mốc, không tính là vàng mới nhận. Mỗi lần server cập
nhật ví, chỉ chênh lệch dương được cộng vào tổng nhận và cửa sổ tốc độ;
chi tiêu giảm số dư hiện tại nhưng không trừ tổng đã nhận. Đây là tổng vàng
nhận qua mọi cập nhật ví quan sát được (kể cả bán đồ/thưởng), không riêng vàng
rơi từ quái và không phải lợi nhuận ròng. Nếu server gộp thu/chi trong một
cập nhật thì client chỉ biết phần thay đổi cuối cùng. Đổi nhân vật/server hoặc
ngắt kết nối xóa số liệu phiên; không lưu tổng vàng vào RMS. Đồng hồ lùi chỉ
khởi động lại phép đo tốc độ vàng, giữ số dư và tổng vàng đã nhận.

ID nhân vật đến từ `readShort()` native: số âm và 0 đều được giữ nguyên để
định danh, không dùng điều kiện `ID > 0` làm tín hiệu sẵn sàng. Phiên sẵn sàng
khi nhận snapshot nhân vật; gói ví đến trước đó được giữ làm mốc ban đầu.
Số dư hiện tại và tổng nhận phải có ngay khi mốc ví gắn với phiên, chỉ tốc độ
vàng/giờ mới chờ năm phút. Bản economy đầu tiên đã lọc sai dấu ID, khiến các
nhân vật có ID không dương giữ `Đang tính toán...`; bản sửa signed-ID bỏ đúng
hai điều kiện này. Đối chiếu `ModUpLv.jar`: HUD đọc trực tiếp vàng của nhân vật,
hiện `Vang: +<số hiện tại - số ban đầu> | TB: <chênh lệch ×3600/giây>/h`.
Tổng nhận của Hung vẫn cộng chênh lệch dương và dùng cửa sổ 5–15 phút theo yêu cầu.

HUD thêm dòng **`Hành trang: xx/xx`** bằng font vàng đậm tại tọa độ logic
`6,110`, bên trái dưới SET1/2/3. Tử số là số phần tử hiện có trong danh sách
hành trang native; mẫu số là dung lượng túi native nhận từ server. Vật phẩm
xếp chồng tính một ô. Chỉ vẽ khi nhân vật/thế giới và dữ liệu túi đã sẵn sàng.

Menu MOD dùng một dòng `Hiện thông tin [BẬT]`, bấm để chuyển
**BẬT → GIẢN LƯỢC → TẮT → BẬT**. BẬT giữ thông tin cũ và thêm hành trang;
GIẢN LƯỢC chỉ có hành trang; TẮT ẩn cả hai. Cài mới mặc định BẬT; lần đầu
nâng cấp kế thừa cờ BẬT/TẮT cũ. Lựa chọn được lưu trong RMS riêng
`hbs_inventory_info_v1`; x3 thêm prefix a/b/c để ba client lưu độc lập.

Các số là ước lượng từ dữ liệu client nhận, không phải số EXP tuyệt đối do
server cung cấp. Số nguyên là cách làm tròn hiển thị; giới hạn của bảng tổng
EXP/phần trăm vẫn áp dụng: [EXP estimate](HUNG_BY_SON_EXP_APPROX.md).

## Source và build

Logic: `src/luna/features/ExpRateSupport.java` và `ExpEstimateSupport.java`.
UI/bridge/input native ban đầu: `tools/luna/HungBySonExpInfoUiBuilder.java`.
Bản sửa icon/số/lifetime dùng `HungBySonExpInfoPolishPatcher.java` và
`HungBySonExpInfoModalGuard.java`. Bản thay bằng SVG của người dùng dùng
`HungBySonExpSvgPatcher.java`; SVG gốc được giữ nguyên byte ở
`src/luna/ui/icons/exp-info.svg`, PNG ở cùng thư mục. `RenderExpInfoSvg.java`
đọc ba path/màu của SVG với crispEdges và xác minh kích thước bằng native A.
`ExpInfoIcon.java`
phụ trách nạp/scale ảnh. Bản economy dùng `GoldRateSupport`,
`HungGoldTrackingPatch`, `HungInventoryInfo.java.in`, `HungInventoryInfoPatch`
và `HungEconomyInfoPatcher`. Menu/HUD chỉ đọc cài đặt RMS một lần rồi cache;
bấm đổi chế độ mới ghi lại. Không tạo danh sách packet theo mỗi frame.

Pipeline bản economy: `build-hung-auto-pk.ps1 -EconomyInfo`, sau đó
`publish-hung-auto-pk.ps1 -EconomyInfo -BuildTag <verified-tag>`.
Các lệnh SVG bên dưới chỉ dùng để tái hiện phiên bản lịch sử.

```powershell
.\tools\luna\build-hung-exp-svg.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>
.\tools\luna\publish-hung-exp-svg.ps1 -StageBuild <verified-stage>
```

Build SVG chỉ nhận đúng bản x1 đã sửa icon/số/lifetime (hash `3E09C037…`)
làm nguồn; sau khi publish cần truyền `-InputJar` là backup đúng bản đó.
Pipeline info và polish trước đó vẫn giữ để tái hiện lịch sử. Chạy kiểm tra số
học với oracle BigInteger ở host, vòng đời phiên, hồi quy EXP/phần trăm,
packet/RMS, thao tác chạm và đóng modal native, class/resource/inverse checks
và startup giả lập. Sau đó xem ảnh HUD/modal trước khi publish. Hash,
backup và kết quả kiểm thực tế được ghi tại [RELEASES.md](RELEASES.md).

## Bản X3

X3 đóng gói nguyên bản x1 đã duyệt bằng `HungBySonX3Packer`, giữ launcher
`MrQuyet.MrQuyet` và cách giữ `*` để chuyển client. Cả ba namespace có
phần trăm ba chữ số, icon 12×12, bảng EXP và điều kiện chờ giống x1.
EXP/giờ, pha nội suy, bảng đang mở và trạng thái xác nhận độc lập. Chỉ backend
bảng tổng EXP theo cấp dùng chung; đóng/mở lại một client không xóa dữ liệu
hay bảng của client khác. Prefix RMS tài khoản/cấu hình vẫn là a/b/c như cũ.

```powershell
.\tools\luna\build-hung-exp-info-x3.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag>
.\tools\luna\publish-hung-exp-info-x3.ps1 -StageBuild <verified-stage>
```

Pipeline pin đúng nguồn x1 có SVG, template launcher và hash X3 trước khi
thay. Kiểm toàn bộ class/resource, parity ba namespace, RMS/vòng đời,
Auto đánh/Farm, EXP native và thao tác nút/bảng EXP ở từng client trước
khi tạo `verified.json`. Việc kiểm giả lập không thay thế chơi server thật.
