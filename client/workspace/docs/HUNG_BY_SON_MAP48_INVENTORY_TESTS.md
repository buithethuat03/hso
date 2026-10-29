# Hung By Son — map 48, hành trang và nhịp sau di chuyển

Ngày kiểm: 2026-10-14. [Hướng dẫn sử dụng](HUNG_BY_SON_AUTO_ATTACK.md).
Hash và trạng thái phát hành được ghi tại [RELEASES.md](RELEASES.md).

## Hành vi đã sửa

- Map ID 48 không đọc, học, khôi phục, kiểm tra hồi sinh hoặc ghi cache quái.
  Flush khi thoát/chuyển map và retry không ghi RMS trên map này. Quái native
  giữ object và HP của server. Record map 48 từ bản cũ bị loại khi nạp trên map
  khác và biến mất khỏi RMS ở lần ghi hợp lệ tiếp theo; các map khác được giữ.
- Đòn Auto đánh đầu sau khi dừng di chuyển trong cùng world bắt đầu từ mốc
  100ms, dù setting là 1/650/1000ms. Chiêu chưa khởi tạo thành công giữ ngoại lệ
  này cho lần thử kế tiếp; sau một lần thành công, các đòn sau dùng setting.
  Cast native lưu cờ riêng cho đòn đầu, vì vậy request không bị kéo lại bởi
  delay 650/1000. Request vẫn dùng khoảng skill native nếu khoảng đó dài hơn.
- Khi mở Hành Trang thật từ world, Auto vẫn chọn chiêu, chạy cast và gửi đòn.
  Phím điều hướng hành trang không được coi là lệnh đi. Game dùng chung khung
  tab cho bán đồ/NPC, nên bridge kiểm đúng singleton, parent world, index hợp
  lệ và panel túi đồ native loại 0. Sell 16/info 15, tab khác, modal, chết,
  mất kết nối và màn hình ngoài world/hành trang vẫn làm Auto tạm dừng.
- Không thêm tick thứ hai: hành trang vốn gọi cập nhật world phía sau; module
  Auto tiếp tục dùng vòng cập nhật có sẵn. Auto Farm giữ nhịp đã đặt; thay đổi
  100ms chỉ áp dụng cho đòn đầu của Auto đánh mới sau di chuyển.
- Chuyển map/khu hoặc thay nhân vật trong lúc vẫn di chuyển không được nhận
  ngoại lệ 100ms. Intent vẫn ON nhưng target/generation cũ bị bỏ và nhịp sau
  khi world ổn định dùng delay đã đặt.

## Bằng chứng bản đơn

Stage cuối: `build/hung-map48-inventory-resume100-20261014-d/`.
[verified.json](../build/hung-map48-inventory-resume100-20261014-d/verified.json).

| Nhóm | Kết quả |
|---|---|
| Auto binary | 1.803 kiểm tra trên candidate: menu/input, target, skill/cast/request thật, RMS, handoff Farm, hủy callback cũ, 99/100ms sau di chuyển, request 100/101ms và native interval 1500/1501ms. |
| Hành trang native | Khởi tạo tab native, vào bằng method native; giữ đúng màn hình trong khi callback module bắt đầu chiêu và serializer gửi một packet quái. Kiểm Sell/info/index sai/parent sai/màn hình khác/modal/wait/chết và phím điều hướng. |
| Semantic | 170 kiểm tra; delay 1/100/650/1000, retry chiêu chưa khởi tạo, nhịp đòn tiếp theo, di chuyển lặp lại và ready map/zone/player change trong lúc đi. Năm suite semantic/controller/route/combat/material đều đạt. |
| Cache | 235 kiểm tra: fresh map48 không load/save, legacy48 bị loại, quái native giữ nguyên, 49→48→49 với dirty record và save thất bại, forced flush/retry không ghi ở48, dữ liệu49/50 còn và được phục hồi. |
| Nhịp/input | 2.573 native timing, 24 Canvas keyboard/touch; x1/x2/x3/x0.8/x0.9; Farm và đánh tay/remote giữ đúng cadence. |
| Quái/animation | Stale99+16, selection46+native, crowded45.044+27, recovery36, remote-animation477. |
| Tính năng cũ | EXP38.735/native132/two-process RMS; tempo94, Noclip24/349, offline MOD94, enhancement13/NPC17, trade, Auto Login242. |
| Archive/runtime | 258 class, 636 introduced member references resolved, class linkage `-Xverify:all`; 778 non-class entries nguyên byte. 8 lớp hook/bridge nền và 5 lớp cache trong allow-list; 9 lớp Auto thêm mới như bản trước. Native hook inverse đạt, class version/API CLDC giữ. Startup12s với memory RMS riêng, stderr rỗng. |

Log: [Auto/native](../build/hung-map48-inventory-resume100-20261014-d/auto-attack-binary.txt),
[cache](../build/hung-map48-inventory-resume100-20261014-d/HungBySonMonsterCacheHostTest.txt),
[timing](../build/hung-map48-inventory-resume100-20261014-d/real650-preserved-timing.txt),
[archive](../build/hung-map48-inventory-resume100-20261014-d/archive-boundary.txt).

## X3 và phát hành

Đã thay [bản đơn](../../jar/Hung_By_Son.jar) và
[X3](../../jar/Hung_By_Son_x3.jar). Publisher backup, atomic replace và đọc
lại hash; các endpoint khác cùng golden giữ nguyên.

| Bản | Nghiệm thu | Hồ sơ phát hành | Backup |
|---|---|---|---|
| Single | [verified](../build/hung-map48-inventory-resume100-20261014-d/verified.json) | [publish](../build/hung-map48-inventory-resume100-20261014-d/publish.json) | [Single trước sửa](../../jar/backup/Hung_By_Son-before-hung-map48-inventory-resume100-20261014-d.jar) |
| X3 | [verified](../build/hung-map48-inventory-resume100-x3-20261014-c/verified.json) | [publish](../build/hung-map48-inventory-resume100-x3-20261014-c/publish.json) | [X3 trước sửa](../../jar/backup/Hung_By_Son_x3-before-hung-map48-inventory-resume100-x3-20261014-c.jar) |

X3 stage cuối là `build/hung-map48-inventory-resume100-x3-20261014-c/`, lấy đúng
bản đơn đã phát hành. 797 class linkage và toàn bộ parity ba namespace đạt;
1.854 host checks gồm map48 trong cả ba store, dữ liệu map khác, reset slot và
EXP chung. Auto/UI/RMS299 (UI72/write191/read29/parent7) kiểm 1/650/1000ms,
99/100ms, retry, cadence tiếp theo, ready map/zone change khi còn đi,
inventory type0/Sell/info/index sai và phím điều hướng trên cả ba slot.
EXP364, crowded74, semantic170 và năm suite hồi quy đạt. Giữ `*` và chuyển
1→2→3→1 giữ cùng instance/Canvas và ba loop sống; stderr rỗng. 19 wrapper
template, icon và payload resource source giữ nguyên; manifest/INI mang
identity launcher như X3 trước, chỉ root EXP được phép tham chiếu giữa slot.

Log: [X3 host](../build/hung-map48-inventory-resume100-x3-20261014-c/x3-host.txt),
[Auto/UI](../build/hung-map48-inventory-resume100-x3-20261014-c/auto-attack-x3-host.txt),
[EXP](../build/hung-map48-inventory-resume100-x3-20261014-c/exp-native-x3.txt),
[launcher](../build/hung-map48-inventory-resume100-x3-20261014-c/launcher.out.log).

Ảnh đã kiểm: [HUD240](../build/hung-map48-inventory-resume100-20261014-d/screenshots/auto-attack-hud-240.png),
[launcher](../build/hung-map48-inventory-resume100-x3-20261014-c/screenshots/launcher/launcher.png).

Lần thay single đầu bị Windows từ chối vì giả lập Zang giữ file. Người dùng
đã đóng giả lập; publisher resume dùng đúng backup và preimage đã kiểm.
Các stage single a/b/c và X3 a/b không phát hành. Stage X3 a/b đã giúp kiểm
nhánh build từ candidate đã nghiệm thu khi endpoint khóa; bản phát hành cuối
được build lại từ endpoint single đã cập nhật, không dùng nhánh đó.

## Phạm vi

Kiểm binary và MicroEmulator, không đăng nhập tài khoản hoặc chơi server thật.
Fixture chạy selector/cast/serializer thật, thu packet tại transport kiểm thử.
Native inventory update/world-parent và module tick được đối chiếu bytecode;
test runtime đi qua entry tab và callback module thật, không dựng một phiên
gameplay server hoàn chỉnh. Cooldown, mana, animation và tick vẫn có thể làm
đòn thực tế muộn hơn 100ms. RMS schema hiện có không đổi.

X3 chạy packed bridge/native key movement và virtual UI thật; full selector,
skill/animation/serializer được nghiệm thu ở đúng source single và ràng buộc
bằng complete slot parity, không suy ra gameplay server từ emulator startup.

Trace actor thiếu assets trong stale fixture cũ giữ phạm vi cache/parser như
báo cáo trước; animation được kiểm riêng bằng477 bài và startup stderr rỗng.

## Kiểm tra PK giữ xác trong khu — 2026-10-16

JAR phát hành vẫn đúng hash ở RELEASES.md, không sửa hoặc build lại JAR.
Fixture [PkCorpseHostTest](../fixtures/hung-by-son/HungBySonAutoAttackPkCorpseHostTest.java)
chạy controller và predicate từ bản đơn hiện hành, tái dùng native world
fixture; ranh giới khởi tạo chiêu được stub, không gửi packet lên server.
11 kiểm tra đạt: bắt đầu giữ người A sống, đổi HP A thành0 mà không xóa A
khỏi native list, request cũ bị từ chối trước tick, tick chọn người B, đòn
mới chờ delay650; trạng thái chết4 vẫn bị loại nếu HP cũ còn dương. Có quái
hợp lệ gần hơn thì mode6 chọn quái; chỉ còn xác thì target=null.
[Log](../build/hung-pk-corpse-check-20261016/result.txt).
