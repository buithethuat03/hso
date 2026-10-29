# HSO CHILD X3

Yêu cầu chỉ tạo x3/x5/… từ x1: dùng [BUILD_XN.md](BUILD_XN.md) và `tools/luna/build-xn.ps1`, không chạy lại pipeline kiểm thử legacy bên dưới. Tài liệu này dành cho cấu trúc bản x3 cũ và điều tra chuyên sâu.

Đường dẫn/lệnh tính từ `client/workspace/`. Bản x3 dùng cơ chế của template người dùng chỉ định: `../jar/backup/autotrade_v27_revive05_horse_x3.jar`. Một launcher `MrQuyet.MrQuyet` quản lý ba virtual client trong cùng một lần chạy. Mở client từ menu; giữ `*` khoảng 500ms để trở về menu rồi chọn client khác. Client đã mở vẫn giữ instance và vòng lặp game.

| Dòng trong menu | Virtual MIDlet | Game namespace | Prefix RMS |
|---|---|---|---|
| HSO CHILD 1 | TemMidlet | default | a |
| HSO CHILD 2 | b.TemMidlet | b/ | b |
| HSO CHILD 3 | c.TemMidlet | c/ | c |

Manifest chỉ khai báo một native MIDlet là `MrQuyet.MrQuyet`; các thuộc tính `0`, `1`, `2` chứa thông tin ba virtual client theo format của template. Suite có tên `HSO CHILD X3`, vendor `HSO_X3`. INI virtual client lấy từ manifest của source HSO CHILD. Dữ liệu của bản single và template Luna không được tự nhập.

## Nguồn game và wrapper

- Game: toàn bộ class/resource của `../jar/hsochild_final.jar`. Các support class có sẵn được patch trực tiếp từ binary này, không compile lại từ source legacy.
- Wrapper: 19 class `lib/*` và `MrQuyet/*` cùng icon `MrQuyet/mid.png` được giữ byte-identical từ template. Không lấy game logic Luna của template để thay game HSO CHILD.
- MIDlet/canvas/display/textbox được chuyển sang API virtual của thư viện mẫu. Canvas callbacks đổi thành `PAINT`, `leyPressed`, `leyReleased` để wrapper giữ được gesture chuyển client.
- `Static` được sinh cho từng namespace từ class/field của source hiện hành. Các class concrete đăng ký ID; `cinitclone` giữ phần khởi tạo static, `clears` giải phóng/reset các static field không có ConstantValue. Đây là lifecycle cần cho việc đóng/mở lại virtual client qua thư viện mẫu.
- Open/list/delete RMS đi qua aliases `a/b/c` tương ứng của `lib.RecordStore`. Hai literal reflective `hsosky_cfg` cũng có prefix riêng. Resource paths tương đối được chuyển về root để cả ba namespace dùng chung resource.

Tài liệu lịch sử [client.md](../../../docs/instruction-history/2026-09-24/client.md) và [AUTOTRADE_NOTES.md](../AUTOTRADE_NOTES.md) chỉ mô tả cập nhật support bằng `RepackSupportX3` trên một JAR x3 đã có cấu trúc này. Nhân class rồi khai báo ba native MIDlet riêng không tái tạo được launcher của template.

## Build và kiểm tra

```powershell
.\tools\legacy\luna\build-hsochild-x3.ps1 -DependencyDir C:\path\to\luna-deps
```

Dependency ngoài repo: `asm-9.7.jar`, `asm-tree-9.7.jar`, `microemulator.jar`. Có thể dùng `LUNA_DEPS`. Template mặc định là file backup ở trên; `HsoChildX3Packer` kiểm hash/ABI template trước khi sử dụng. Script từ chối output đã tồn tại, tạo artifact mới trong `build/releases/`, không tự publish. Chạy từ workspace; không dùng pipeline Luna có bước dọn toàn bộ release root.

`HsoChildX3Packer` dùng remapper CLDC của `RepackSupportX3`, đồng thời xử lý frame types, inner/outer class metadata, tên class dùng qua reflection và toàn bộ package `luna/`. Signature/annotation không được hỗ trợ sẽ làm build dừng. `X3SlotSupport.java.in` được compile UTF-8 với `--release 7`, rồi đổi package cùng client.

Script kiểm archive/resource/wrapper integrity, class references/versions, JVM class/member loading, native và reflective RMS isolation, static clear/restart, symbol catalog và legacy route cho cả ba namespace. Semantic/controller/route fixtures của source hiện có được kiểm riêng, không thay binary game.

`HsoChildX3LauncherTest` khởi động một launcher trong MicroEmulator, kiểm menu có ba client, khởi động cả ba, gửi giữ `*` qua callback thật rồi chuyển 1 → 2 → 3 → 1. Test kiểm instance/canvas không bị thay và cả ba vòng lặp game còn sống. Logs và screenshots nằm trong `build/<OutputName>/`. Runtime dùng memory RMS và `user.home` riêng dưới `build/`.

Để chạy launcher headless thủ công:

```powershell
java -Xverify:all '-Djava.awt.headless=true' -cp C:\path\to\luna-deps\microemulator.jar org.microemu.app.Headless build\releases\<output>.jar
```

Kiểm giả lập không thay thế việc đăng nhập/chơi ba tài khoản trên server thật.

Hash, trạng thái kiểm tra và artifact phát hành chỉ ghi tại [RELEASES.md](RELEASES.md).

## Biến thể hsochild_update X3

`../jar/hsochild_update_x3.jar` lấy toàn bộ game từ bản single
`../jar/hsochild_update.jar`, dùng cùng launcher/template ở trên. Đây là
binary Hung-derived đã đổi server/login key và data identity; không dùng
game của `hsochild_final.jar` hoặc template Luna để thay source này.

Suite `HSOCHILD X3`, vendor `HSOCHILD_X3`; menu `HSOCHILD 1/2/3` mở ba
virtual client ở default, `b/`, `c/`. Giữ `*` khoảng 500ms để về menu,
rồi chọn client khác. RMS thực tế lần lượt có prefix `aHSOCHILD_`,
`bHSOCHILD_`, `cHSOCHILD_`; độc lập với bản single `HSOCHILD_` và data
Investigate `inv_`. Mỗi client chỉ có `Server lậu` (index 0), endpoint/key
đã sửa ở bản single, ngưỡng dùng chiêu 300ms thực và menu tốc độ
x1/x2/x3/x5/x10. Những skill có cooldown native dài hơn vẫn giữ cooldown đó.

Packer dùng `tools/luna/HungBySonX3Packer.java` cùng các utilities/remapper
của pipeline trên. Sau sáu tham số hiện có (`source.jar`, `template.jar`,
`new.jar`, `X3SlotSupport.class`, `report.txt`, `expected-source-sha`),
truyền tham số cuối `HSOCHILD` để dùng identity và virtual INI riêng.
Không truyền tham số này thì hành vi đóng gói Hung hiện có giữ nguyên.

`HungBySonX3HostTest` nhận cùng tùy chọn `HSOCHILD` để kiểm identity và
store names mới. `HungBySonChildLoginBinaryTest` nhận namespace tùy chọn
`b/` hoặc `c/` để chạy writer/endpoint probe của từng client; bỏ qua cho
root. Launcher fixture nhận label `HSOCHILD`. Kết quả kiểm tra chi tiết,
hash và giới hạn server thật ở [RELEASES.md](RELEASES.md).

## Biến thể Chibi Up X3

`../jar/chibi_up_x3.jar` ghép đúng game của `../jar/chibi_up.jar` vào cùng
launcher mẫu. Suite `HSO CHIBI X3`, vendor `HSO_CHIBI_X3`; menu có
`HSO CHIBI 1/2/3`. Giữ `*` khoảng 500ms để trở về menu và chuyển client.
Tài khoản, lựa chọn quái và thiết lập Auto đánh dùng các prefix
`aHSO_CHIBI_`, `bHSO_CHIBI_`, `cHSO_CHIBI_`, độc lập với bản đơn và các
suite khác. Bảng EXP vẫn dùng chung trong suite x3, qua store
`aHSO_CHIBI_hbs_exp_levels_v2`, không nhập EXP từ bản đơn.

Giữ endpoint/login Chibi và toàn bộ profile tắt cache quái của bản nguồn:
không đọc/ghi/khôi phục cache hay giữ xác quái. Không biên dịch lại game
support để đóng gói. Wrapper/icon và mọi resource game giữ nguyên bytes.

Build: `tools/luna/build-chibi-up-x3.ps1 -DependencyDir <dependencies>
-BuildTag <fresh-tag>`, thêm `-Publish` để phát hành sau các kiểm tra.
Packer nhận tùy chọn cuối `CHIBI`. `ChibiX3HostTest` kiểm bản sao với nguồn,
liên kết class, dữ liệu và vòng đời; fixture launcher kiểm ba client cùng
chạy và giữ `*`/chuyển `1 → 2 → 3 → 1`. Kết quả và giới hạn kiểm server thật
được ghi trong [RELEASES.md](RELEASES.md).

## Biến thể Chibi Final X3

`../jar/chibi_final_x3.jar` được ghép từ đúng `../jar/chibi_final.jar` đã sửa
login và khởi tạo nhân vật. Nó là biến thể riêng với `chibi_up_x3.jar`:
suite **HSO CHIBI FINAL X3**, vendor `HSO_CHIBI_FINAL_X3`, menu
**HSO CHIBI FINAL 1 / 2 / 3**. Mỗi client giữ một phiên và dữ liệu riêng;
giữ `*` khoảng 500ms để về menu rồi chọn client khác. Các client đã mở
tiếp tục giữ instance và vòng lặp game.

Cả 217 class nguồn được chép đủ trong default, `b/`, `c/`; các sửa chữ ký
đăng nhập, số lượng túi/kho và dung lượng túi ở gói nhân vật đều có trong
ba namespace. RMS native có prefix a/b/c và cấu hình mod có prefix tương
ứng; không tự nhập tài khoản từ bản đơn. Resource game dùng chung và giữ
nguyên byte. Launcher/icon lấy từ cùng template đã xác minh ở trên.

Build bằng `tools/luna/build-chibi-final-x3.ps1 -DependencyDir <dependencies>
-BuildTag <fresh-tag>`, thêm `-Publish` để phát hành sau khi kiểm tra.
`ChibiFinalX3Packer` ghép binary nguồn trực tiếp, không compile lại tính năng.
`ChibiFinalX3HostTest` kiểm 676 class, parity đầy đủ với nguồn, các sửa packet,
dữ liệu riêng, list/delete và static restart. Fixture launcher kiểm chuyển
1→2→3→1 bằng callback giữ `*` thật. Trạng thái và hash cụ thể chỉ ghi trong
[RELEASES.md](RELEASES.md); kiểm giả lập không xác nhận đăng nhập server thật.
