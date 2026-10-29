# Nghiên cứu ước lượng EXP từ packet nhận và phần trăm HUD

Ngày: 2026-10-12. Phạm vi: nghiên cứu thuật toán cho bản đơn `Hung_By_Son.jar`; chưa sửa source tính năng, patch/build hoặc thay JAR.

JAR khảo sát: `../jar/Hung_By_Son.jar` tính từ workspace, SHA-256 `8B67184EE5AEBD1181931B80CD6F9EF0AC833637BE90B5EB5C5AA14289A55953`.

**Kết luận:** có thể ước lượng EXP cần để đi hết cấp hiện tại bằng dữ liệu client đang nhận, với điều kiện số EXP báo trong packet tương ứng với EXP thực được cộng và phần trăm phản ánh tuyến tính cùng một tổng EXP của cấp. Kết quả phải mang nhãn `Approx.`; sai số tính được dưới đây chỉ là sai số lượng tử hóa, không phải bảo đảm sai số tổng thể trên server thật.

## Dữ liệu đã xác minh

- HUD lấy một số nguyên theo đơn vị 0,1 điểm phần trăm: `311` được hiển thị thành `31,1%`.
- Handler EXP đã kiểm tra đọc ID nhân vật bằng `readShort`, tiến độ bằng `readShort`, EXP vừa nhận bằng `readInt`; số cuối hiện dùng làm chữ nổi `<integer>xp`. Theo tài liệu hành vi, đây là packet 30/type 1.
- Phần trăm và EXP vừa nhận nằm trong cùng handler. Có thể lấy mẫu sau khi đọc đủ dữ liệu, trước điều kiện hiển thị popup. Không đếm đối tượng chữ nổi qua các frame.
- Cấp được cập nhật bằng luồng khác; packet EXP không tự mang cấp. Gói EXP vượt ranh giới cấp vì thế phải được coi là mẫu không an toàn cho ước lượng một cấp.
- Client không cho biết server dùng phép cắt phần lẻ hay làm tròn phần trăm, hoặc EXP báo có luôn bằng EXP thực được cộng hay không. Chưa có trace gameplay thật xác nhận các điều kiện này.

Tham chiếu phần trình bày XP: `HUNG_BY_SON_BEHAVIOR.md`, mục XP popup; `LUNA_BEHAVIOR.md`, phần packet-30/type-1. Tài liệu `PROTOCOL.md` thuộc khảo sát HSOSKY khác, không dùng nó để suy đoán payload Hung.

## Công thức và nguyên nhân không nên đo lần tăng 0,1% đầu

Đặt `T` là EXP cần từ 0% tới 100% của cấp hiện tại, `E0` là EXP lúc bắt đầu đo, `S_i` là tổng EXP nhận kể từ mốc đó, `q_i` là tiến độ nguyên theo đơn vị 0,1%.

Với giả thiết server cắt phần lẻ:

`q_i = floor(1000 * (E0 + S_i) / T)`.

Giữa hai mẫu cùng cấp, đặt `S = S_j - S_i` và `D = q_j - q_i`. Ước lượng đơn giản là:

`T_est = 1000 * S / D`, khi `D > 0`.

Ví dụ: tiến độ từ 30,1% tới 31,1%, nhận tổng cộng 1.000.000 EXP. `D = 10`, suy ra `T_est = 100.000.000 EXP`.

Tuy nhiên nếu lúc bắt đầu đã gần sát mốc tăng phần trăm, chỉ cần một ít EXP nữa là HUD tăng 0,1%. Với `T = 100M`, một bước đầy đủ là 100.000 EXP. Bắt đầu tại EXP 30.099.000 và nhận 1.000 EXP sẽ đổi HUD từ 30,0% tới 30,1%; nhân 1.000 với 1.000 sẽ đoán sai thành `T = 1M`.

Một lần vượt mốc không xác định được toàn bộ chiều rộng bước. Đo giữa hai lần vừa vượt mốc tốt hơn, nhưng vẫn có EXP vượt quá ranh giới trong mỗi packet; không thể coi mọi lần vượt mốc là đúng tại ranh giới.

## Ước lượng một khoảng thay vì một số duy nhất

Phần lẻ bị mất ở hai đầu cho quan hệ:

`D - 1 < 1000 * S / T < D + 1`.

Khi `D > 1`:

`1000 * S / (D + 1) < T < 1000 * S / (D - 1)`.

Khi `D = 1`, chỉ suy ra `T > 500 * S`, không có cận trên hữu hạn. Khi `D = 0`, các mẫu chưa tăng phần trăm vẫn hữu ích: `T > 1000 * S`. Các công thức cũng dùng được cho phép làm tròn gần nhất với một quy tắc ổn định; khi chưa biết quy tắc/tie, nên giữ cận bảo thủ và dung sai ở ranh giới.

Ví dụ cận từ chỉ hai đầu, với tổng nhận tương ứng một cấp thật 100M:

| Tiến độ tăng quan sát được | Tổng EXP quan sát trong ví dụ | Khoảng T suy ra |
|---|---:|---:|
| 0,1 điểm % | 100.000 | >50M, chưa có cận trên |
| 0,5 điểm % | 500.000 | 83,33M–125M |
| 1,0 điểm % | 1.000.000 | 90,91M–111,11M |
| 5,0 điểm % | 5.000.000 | 98,04M–102,04M |
| 10,0 điểm % | 10.000.000 | 99,01M–101,01M |

Lấy giao của khoảng từ nhiều cặp mẫu để siết cận: `T_low = max(cận dưới)`, `T_high = min(cận trên)`. Các mẫu chưa đổi phần trăm cũng đóng góp. Không lấy trung bình các kết quả từ từng lần tăng 0,1%, vì kết quả đầu có thể lệch theo vị trí ban đầu, và phép chia trên các đoạn ngắn khuếch đại sai số.

Nếu có cận trên hữu hạn, chọn trung điểm khoảng làm số trình bày, dùng `(T_high-T_low)/(T_high+T_low)` làm độ rộng tương đối quanh trung điểm. Đây là chỉ số độ hẹp của khoảng theo mô hình, không phải xác suất tin cậy 95%.

Giao cận của các cặp đủ để có một pha ban đầu thực phù hợp trong mô hình không bị giới hạn cấp. Nếu cần kiểm chứng ở đơn vị EXP nguyên cho một `T` ứng viên, giao các khoảng:

`ceil(q_i*T/1000) - S_i <= E0 <= ceil((q_i+1)*T/1000) - 1 - S_i`.

Đồng thời yêu cầu `E0 >= 0` và `E0 + S_max < T`. Quy tắc làm tròn khác phải được kiểm tra riêng, không trộn mỗi mẫu với một quy tắc tùy ý. Kiểm tra EXP nguyên có thể tạo vài giá trị T không hợp lệ bên trong khoảng tổng; HUD vẫn chỉ cần giá trị ước lượng, không tuyên bố đã suy ra T chính xác.

## Luồng đề xuất khi triển khai

1. Chỉ nhận mẫu của đúng nhân vật đang điều khiển. Sau một packet đầy đủ, cộng EXP của packet rồi ghi mẫu `(cấp, tổng EXP đã đếm, tiến độ)`; số EXP giống nhau vẫn là các lần nhận hợp lệ khác nhau.
2. Giữ mốc đầu, mẫu gần nhất và một tập nhỏ 32–64 mốc, ưu tiên mẫu ngay trước/sau lần đổi phần trăm và các mốc cách xa nhau. So sánh mẫu mới với các mốc còn giữ để cập nhật cận. Bỏ mốc cũ vẫn có thể giữ cận đã suy ra trong một đoạn sạch; khi bắt đầu đoạn mới phải bỏ cận thuộc đoạn bị nghi ngờ.
3. Ban đầu hiện `(dang do EXP...)`. Chỉ hiện số khi khoảng hữu hạn và độ rộng đủ nhỏ. Đề xuất ngưỡng ban đầu 5%, sau đó cập nhật dần khi khoảng hẹp hơn và các đoạn kiểm tra tiếp theo phù hợp. Đây là lựa chọn UX, chưa phải ngưỡng đã thử trên server thật; không bắt buộc phải tăng đúng một số % cố định mới có kết quả.
4. Dòng HUD ví dụ: `(31.1M/100M Approx.)`. Mẫu số là T ước lượng, tử số đơn giản lấy phần trăm hiện tại nhân T ước lượng. Tử số này không phải tổng EXP đếm từ lúc đăng nhập. Có thể làm mượt theo từng packet sau khi ước lượng được pha E0, nhưng đó là bước nâng cấp cần xác minh cách server làm tròn.
5. Chỉ tính lại khi có sự kiện EXP/trạng thái; paint dùng chuỗi cache. Dùng `long` cho tổng và phép nhân, kiểm tra overflow trước `1000L*S`; không cấp phát lớn theo frame. Một tập mốc hữu hạn phù hợp CLDC 1.1. Chữ phải dùng font HUD có đủ ký tự, kiểm tra độ rộng và phần khung tăng chiều cao.

## Reset và giới hạn

- Đổi cấp: bỏ mẫu vượt cấp và đoạn cũ, chờ trạng thái cấp/phần trăm mới ổn định rồi đặt mốc mới; không chia EXP của một packet qua hai cấp khi thiếu dữ liệu.
- Giảm phần trăm, EXP âm, thay nhân vật/server, mất kết nối hoặc khoảng theo dõi không rõ: dừng đoạn đo và đặt lại mốc. Đổi map riêng lẻ không cần reset nếu danh tính và luồng cập nhật liên tục.
- Cập nhật phần trăm ngoài luồng EXP phải được đồng bộ trước khi dùng; không trộn snapshot và delta lệch thứ tự. Giao khoảng rỗng là dấu hiệu mất tính nhất quán, cần bỏ kết quả hiện hành và đo lại.
- Bonus/party/nhiệm vụ đều dùng được nếu số báo là EXP thực được cộng và tất cả thay đổi đều được quan sát. Nếu server hiện EXP trước bonus nhưng cộng EXP sau bonus, thuật toán học một T sai dù khoảng rất hẹp. Một hệ số sai ổn định không thể tự phát hiện chỉ bằng hai dữ liệu này.
- Nếu lưu kết quả, khóa theo endpoint + nhân vật + cấp + phiên bản estimator; sau reconnect chỉ dùng như kết quả cũ cần xác minh lại, không nối tổng EXP qua khoảng không quan sát. Triển khai đầu nên chưa cache qua phiên để dễ kiểm chứng.

## Kiểm tra nghiên cứu đã chạy

Mô phỏng Python trực tiếp, seed `20261012`, không thay JAR:

- 10.000 lần bắt đầu tại pha ngẫu nhiên với T thật 100M, mỗi packet 500–10.000 EXP: phép đo tới lần vượt 0,1% đầu cho T ước lượng từ khoảng 0,507M đến 108,433M, trung vị 53,42M. Đây là kết quả của phân bố mô phỏng này, không phải thống kê server thật.
- 2.000 bộ mẫu sạch, 64 packet/bộ, cả mô hình cắt phần lẻ và làm tròn: khoảng giao nhiều cặp chứa T thật trong 2.000/2.000 bộ. Độ rộng tương đối nửa khoảng có trung vị khoảng 0,155%, lớn nhất khoảng 0,491%; các bộ này có bước nhận tương đối lớn và chỉ kiểm tra tính đúng toán học.
- Mẫu bonus ẩn: packet báo 50.000 EXP nhưng thực cộng 100.000 EXP, T thật 100M. Thuật toán cho khoảng khoảng 49,50M–50,51M. Kết quả này xác nhận khoảng hẹp không loại trừ sai số hệ thống.

Trước khi triển khai cần fixture cho: nhiều packet không tăng phần trăm, vượt mốc lớn trong một packet, EXP lặp cùng số, ID người khác, mất EXP, gain vượt cấp trước/sau packet cập nhật cấp, snapshot làm thay đổi tiến độ, reconnect và bonus sai hệ số. Sau đó đối chiếu một đoạn gameplay thật để kiểm tra giả thiết EXP báo bằng EXP thực cộng. Nghiên cứu hiện tại đủ để đề xuất thuật toán, chưa chứng minh độ chính xác trên server thật.
