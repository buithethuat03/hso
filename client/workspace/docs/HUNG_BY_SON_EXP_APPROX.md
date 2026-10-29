# Hung By Son — EXP ước lượng trên HUD

Tính năng áp dụng cho bản đơn và cả ba slot X3. Dòng level/phần trăm native được giữ; phía dưới có thêm:

```text
Đang tính toán...
```

Sau khi có đủ dữ liệu hoặc đã load kết quả từ RMS:

```text
(37.4M/100M)
```

## Hành vi

Bản x1 và cả ba client X3 có nút `i` cạnh dòng EXP để xem EXP/giờ, tổng EXP cấp và thời gian
còn lại; các giá trị có điều kiện đủ dữ liệu riêng. Xem [bảng thông tin
EXP](HUNG_BY_SON_EXP_INFO.md). Chuỗi chờ là `Đang tính toán...`; pipeline
dual-output bên dưới thuộc bản EXP cũ.

- Chỉ đếm EXP trong packet 30 của đúng đối tượng nhân vật đang điều khiển, ngay sau khi đọc đủ dữ liệu và trước điều kiện hiển thị chữ nổi. Chữ nổi bị tắt vẫn học EXP; các packet có cùng số EXP vẫn được cộng riêng.
- Từ mốc sạch của cùng một cấp, giữ tối đa 64 mẫu và giao các cận suy ra từ tổng EXP/phần trăm. Mẫu đầu được công bố sau khi tăng ít nhất 1 điểm phần trăm và có khoảng ước lượng hữu hạn đủ hẹp (nửa độ rộng tương đối tối đa 11%). Tiếp tục lấy mẫu để siết khoảng, không lấy trung bình đơn giản những lần tăng 0,1%.
- Từ bản rolling/fine 2026-10-28, tử số dùng đúng phần trăm nội suy ba chữ số đang hiện nhân tổng EXP cấp ước lượng; chỉ dùng tiến độ native khi chưa có pha nội suy hợp lệ. Cache dòng có cả giá trị fine nên packet làm thay đổi fine có thể cập nhật K/M ngay trong cùng bước 0,1% native. Đây là EXP hiện tại ước lượng, không phải EXP kiếm được từ lúc mở game.
- Định dạng làm tròn tới một chữ số thập phân, bỏ `.0`: `37.4K`, `100M`, `129.4M`, `1229.4M`, `1024M`. Không có đơn vị B. Giá trị dưới 1000 dùng số nguyên; mốc làm tròn `999.95K` được chuyển thành `1M`.
- Theo xác nhận của người dùng rằng bảng EXP giống nhau giữa nhân vật/server, kết quả được lưu **chỉ theo level**. RecordStore `hbs_exp_levels_v2` dùng chung trong cùng ứng dụng, bao gồm cả ba slot X3. Mỗi slot vẫn chỉ lấy mẫu EXP của nhân vật đang điều khiển; đổi nhân vật/server luôn bắt đầu đoạn đo mới, không cộng nối EXP giữa các nhân vật. Tài khoản/cache/chọn quái có sẵn giữ nguyên các namespace riêng.
- Lưu ngay lần đầu đủ mẫu; cập nhật tiếp theo có throttle 5 giây, flush khi nhận snapshot phiên mới hoặc ngắt/đóng phiên. Bảng giữ tối đa 255 level. Chỉ gửi các level vừa thay đổi vào backend, rồi đọc–gộp–ghi dưới một khóa chung; slot có dữ liệu cũ không ghi đè các level khác vừa được slot khác học.
- Login snapshot nạp kết quả sau khi ID nhân vật đã được gán; bảng cache theo level có thể dùng ngay cả khi endpoint/ID chưa hoàn chỉnh. Không nối tổng EXP qua khoảng offline. Đổi cấp thật đặt lại mốc và tra cache của cấp mới; thông báo cùng level không làm mất mẫu. EXP âm, phần trăm giảm, zero EXP kèm thay đổi tiến độ hoặc mẫu mâu thuẫn làm bắt đầu đoạn đo mới. Zero EXP với tiến độ không đổi được bỏ qua.
- Hai lỗi lưu/nạp cũ được xử lý: `InvestigateStorage` có phép nối chuỗi được compiler Java 7 sinh thành `StringBuilder`, thiếu trên CLDC 1.1; thêm vào đó, bước dọn RMS khi login xóa bản ghi `exp_approx_v1`. Backend EXP mới dùng trực tiếp MIDP RMS, không gọi đường nối chuỗi cũ, và nằm ngoài namespace bị dọn. Hook trước bước dọn nhập các bản ghi EXP v1 còn tồn tại ở `inv_`/`ainv_`/`binv_`/`cinv_` vào bảng level v2. Chỉ bản ghi EXP cũ được giữ lại khi dọn; các hành vi dọn khác không đổi. Bản ghi v1 không bị xóa, kể cả khi chuyển sang v2 chưa lưu thành công.
- Kết quả cũ vẫn dùng trong khi đo lại. Đoạn sạch mới không giao với cận cũ có thể thay kết quả cũ sau khi tự đạt ngưỡng đủ mẫu. Điều này cho phép học lại khi bảng EXP server thay đổi.
- HUD kiểm tra khóa phiên sau khi dữ liệu nhân vật đã ổn định và khôi phục kết quả nếu phiên vừa bị ngắt. Khi chưa có kết quả, nó thử đọc lại tối đa mỗi 5 giây để nhận level vừa được slot khác học hoặc phục hồi lỗi đọc tạm thời. Ghi thất bại giữ dữ liệu chờ và cũng được thử lại có throttle. Khi đã có cache hợp lệ và không có cập nhật chờ, render không đọc/ghi RMS hoặc tạo lại chuỗi nếu tiến độ/cận không đổi. Một bản ghi lỗi không làm bỏ các level hợp lệ khác; lỗi đọc không xóa cache trong bộ nhớ.

## HUD và phạm vi binary

### Nội suy phần trăm ba chữ số — x1 và X3, 19/10/2026

`Hung_By_Son.jar` và cả ba client X3 hiển thị phần trăm dạng `37,456%` khi đã có tổng EXP
cấp đủ dùng và ít nhất một lần nhận EXP sạch sau mốc phiên hiện tại. Khi chưa
đủ dữ liệu, vẫn hiện một chữ số như `37,4%`; tổng EXP cấp chưa từng học cần
tăng ít nhất một điểm phần trăm như quy tắc học bên trên. Pha nội suy riêng
cho từng client X3, không cộng EXP của nhiều nhân vật vào cùng một phiên.

`ExpPercentInterpolator` giao khoảng pha từ các mẫu tiến độ/EXP, dùng trung
điểm khoảng tổng EXP cấp. Khi tổng ước lượng không đổi, nó giữ cận pha qua
các lần cộng để không quên thông tin khi cửa sổ 64 mẫu dịch chuyển. Kết quả
được giới hạn trong bước 0,1% server hiện báo và không giảm trong đoạn nhận
EXP dương sạch. Đổi cấp/nhân vật, EXP âm, phần trăm giảm, snapshot mới hoặc
ngắt kết nối bỏ pha cũ; chỉ bảng tổng EXP cấp tiếp tục được lưu RMS.

Phần lẻ giả định server cắt tiến độ theo bước 0,1%. Nếu server làm tròn hoặc
báo EXP khác EXP thực cộng, phần lẻ có thể lệch. Ba chữ số là độ phân giải
hiển thị, không phải bảo đảm chính xác 0,001%. Khoảng mâu thuẫn quay về số
native một chữ số. Thanh XP và field tiến độ native giữ cách tính cũ. Dòng EXP
K/M hiện dùng cùng phần trăm fine; ví dụ 32,012% được tính theo 32,012%, không
chờ tới mốc native 32,1%. K/M vẫn làm tròn một chữ số thập phân nên dạng M đổi
khi vượt ngưỡng làm tròn 0,1M (100.000 EXP), không phải mỗi packet đều thấy chữ
khác. Paint dùng chuỗi cache khi dữ liệu không đổi, không đọc/ghi RMS thêm.

Dòng Lv/phần trăm giữ nguyên font, dấu `+`, dấu phẩy thập phân native,
tọa độ và khung. Chỉ phần số thập phân của phần trăm được bổ sung hai chữ số;
không đổi bố cục để bù chiều dài dòng mới.

Build/kiểm tra riêng từ bản x1 hiện hành đã pin, chỉ publish x1 khi thêm `-Publish`:

```powershell
.\tools\luna\build-hung-exp-percent.ps1 -DependencyDir <dependencies> -BuildTag <fresh-tag> -Publish
```

Khung là hình chữ nhật liền rộng 96px như bản gốc, dùng pixels sprite native hiện có; chiều cao từ 42 lên 56px để đủ dòng EXP. Dòng EXP ở offset y=38 theo tọa độ nội dung, dùng font trắng thường có sẵn để số dạng `1229.4M` vừa trong khung. Các dòng HP/MP/Lv và nút native bên phải giữ font/tọa độ/hitbox. Biểu tượng thư/count và hitbox dùng tọa độ native `h` được dời từ 45 xuống 59 ở cả hai layout.

Theo yêu cầu mới khi khung đã trở về chiều rộng gốc, cả ba dòng thông báo (hai dòng chạy chữ và một thông báo ngắn) giữ đúng vị trí native ban đầu. Không còn hook đổi baseY hoặc mức tối thiểu y76. Patcher chứng minh toàn bộ thân phương thức vẽ thông báo giống nguồn gốc; background, clip, chữ, chiều rộng, thứ tự, tốc độ chạy và thời gian tồn tại đều giữ nguyên.

Patcher phát hiện symbol theo cấu trúc và descriptor trên hash input pinned, quan sát luồng EXP/snapshot/level và bước dọn RMS, dùng bridge sinh tự động và giữ resource/manifest byte-identical. Bản đơn lấy nền `Hung_By_Son_crowded_fix.jar`; output vẫn tên `Hung_By_Son.jar`. X3 được vá đủ root, `b/`, `c/` từ bản X3 crowded trước EXP để giữ launcher/lifecycle/data. Cả hai giữ sửa quái crowded. Support lấy mẫu, bridge và adapter riêng từng slot; chỉ backend bảng EXP/khóa ghi được giữ một bản chung ở root.

Source: `src/luna/features/ExpEstimateSupport.java`, `src/luna/api/ExpEstimateStore.java`; layout thông báo: `src/luna/ui/ExpEstimateNoticeLayout.java`; adapter/backend RMS: `tools/luna/templates/ExpEstimateRmsStore.java.in`, `tools/luna/templates/ExpEstimateSharedRmsStore.java.in`; binary patch: `tools/luna/HungBySonExpPatcher.java`. Source không dùng tên obfuscate. Build staging từ nền crowded:

Lệnh dual-output dưới đây là pipeline lịch sử trước nội suy phần trăm. Không
dùng nó với support hiện tại: class collector và số class helper của pipeline
này chưa hỗ trợ `ExpPercentInterpolator`. Bản x1 hiện hành dùng pipeline
incremental `build-hung-exp-percent.ps1` ở trên, rồi các bước info/polish/SVG.
X3 mới dùng `build-hung-exp-info-x3.ps1` để nhân đúng bản x1 đã duyệt;
xem [bảng thông tin EXP](HUNG_BY_SON_EXP_INFO.md).

```powershell
.\tools\luna\build-hung-exp-estimate.ps1 -SingleInputJar build/hung-crowded-monsters-20261011-e/Hung_By_Son_crowded_fix.jar -ExpectedSingleInputHash F806EE5F395B6979057ECF49DA4C10E7313D39A6F6AB23A10D04E88347A4B892 -DependencyDir <dependencies> -BuildTag <fresh-tag>
```

Build không tự thay JAR phát hành. Hash nguồn và hash bản đang thay là hai thông tin riêng; rebuild sau phát hành cần chỉ định `-ReleaseSingleBeforeHash` và `-ReleaseX3BeforeHash` đúng hash hiện tại. Hash, backup và bằng chứng phát hành được ghi duy nhất trong `RELEASES.md`.

## Giới hạn

Theo yêu cầu người dùng, dòng số không có chữ `Approx.`. Các số vẫn được ước lượng từ mẫu EXP/phần trăm. Thuật toán giả định EXP báo bằng EXP thực được cộng và tiến độ có cùng quy tắc lượng tử hóa tuyến tính trong đoạn đo. Nhiều mẫu giảm sai số do phần trăm chỉ có một chữ số thập phân; không tự loại trừ sai lệch có hệ số ổn định, ví dụ số báo trước bonus nhưng số thực cộng sau bonus. Chưa đối chiếu gameplay/server thật; không coi kiểm tra offline hoặc ảnh giả lập là bằng chứng về độ chính xác trên server thật.

Nghiên cứu toán học: [EXP_ESTIMATION_RESEARCH.md](EXP_ESTIMATION_RESEARCH.md).
