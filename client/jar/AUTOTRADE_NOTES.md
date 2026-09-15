# Auto Đi Buôn — ghi chú kỹ thuật

Tính năng thêm vào menu **"TruongBk MOD"** của `HSOLUNA_TRUONGBK_PERFECT.jar`, nằm ngay dưới
"Auto Nuôi Pet". Output: `HSOLUNA_TRUONGBK_AUTOTRADE.jar`.

## Build

Chạy **từ trong `client/jar`**:

```powershell
# 1. Tra lại bảng ký hiệu (đọc-only, bắt buộc chạy nếu đổi jar gốc)
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" SenderOpcodeReader.java VerifyAutoTradeSymbols.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" VerifyAutoTradeSymbols

# 2. Sinh source support class từ template rồi biên dịch
javac -encoding UTF-8 GenAutoTradeSupport.java
java  GenAutoTradeSupport
# jar phụ mở quyền hai field private của màn hình cửa hàng - PHẢI biên dịch với jar này
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" MakeAutoTradeCompileJar.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" MakeAutoTradeCompileJar
javac -encoding UTF-8 -cp ".autotrade-compile.jar" --release 7 HSOAutoTradeSupport.java

# 3. Vá jar
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradePatcher HSOLUNA_TRUONGBK_PERFECT.jar HSOLUNA_TRUONGBK_AUTOTRADE.jar

# 4. Kiểm tra
javac -encoding UTF-8 TestAutoTradeLoad.java TestAutoTradeRoute.java
java  -Xverify:all TestAutoTradeLoad
java  -Dfile.encoding=UTF-8 TestAutoTradeRoute
```

`AutoTradePatcher` từ chối ghi đè output đã tồn tại. **Mỗi bản sửa ra một tên jar mới**
(`HSOLUNA_TRUONGBK_AUTOTRADE_V2.jar`, `_V3`…) để còn lùi lại được — bản mới nhất ghi trong bảng
jar ở `CLAUDE.md`. Nếu vá đè lên tên cũ mà đang chạy jar đó trong giả lập thì Windows khoá file:
vá ra tên tạm rồi `mv` vào chỗ cũ sau khi đóng game.

## Tại sao phải có bước sinh source

Tên obfuscate trong jar dài **210–250 ký tự**. Viết thẳng vào source thì không đọc được và
rất dễ gõ nhầm. Vì vậy:

| File | Vai trò |
|---|---|
| `HSOAutoTradeSupport.java.in` | **Bản để sửa tay.** Dùng dấu `@khoá@` thay cho tên obfuscate. |
| `autotrade-symbols.properties` | Bảng `khoá = tên obfuscate`. Sinh từ jar, không sửa tay. |
| `GenAutoTradeSupport.java` | Thay `@khoá@` → tên thật, xuất ra `HSOAutoTradeSupport.java`. |
| `HSOAutoTradeSupport.java` | **Artifact build.** Đừng sửa, đừng commit. |

`GenAutoTradeSupport` báo lỗi và dừng nếu template dùng một khoá không có trong bảng — không
bao giờ sinh ra file nửa vời. Lưu ý: trong template **không được dùng tag javadoc `@param` /
`@return`**, vì `@` là ký tự đánh dấu.

## Bảng ký hiệu được kiểm chứng thế nào

Tên obfuscate **bị tái sử dụng** cho cả class, field lẫn method — cùng một chuỗi có thể vừa là
tên class, vừa là tên field trên class khác. Vì vậy mọi phép tra đều phải gắn **owner +
descriptor**, không bao giờ chỉ so tên. Cắt 14 ký tự đầu để so cũng sai vì trùng rất nhiều.

`VerifyAutoTradeSymbols` đối chiếu từng dòng trong properties với jar: đúng kind
(class/field/method), đúng descriptor, đúng static/instance, và có đi lên chuỗi superclass.
Riêng nhóm `sender.*` còn được kiểm bằng cách **đọc ngược opcode gói tin** mà method đó gửi —
đây mới là bằng chứng thật rằng `sender.buy` đúng là lệnh mua.

## Bảng opcode gói tin (đọc từ jar, khớp với `server/src/main/java/client/MessageHandler.java`)

| op | chữ ký client | handler server | dùng để |
|---|---|---|---|
| 4 | `(short,short)` | báo vị trí | di chuyển |
| 7 | `(short)` | `Service.mob_in4` | hỏi thông tin NPC/quái |
| 23 | `(byte)` | `MenuController.request_menu` | mở menu NPC |
| 24 | `(byte,short,short)` | `Service.buy_item` | **mua** (type=1, id ngọc, số lượng) |
| 32 | `(short)` | `UseItem.ProcessItem4` | **dùng vật phẩm túi 4** (Bò vàng) |

Riêng di chuyển thì **không tự bắn gói 4** — engine tự bắn khi nó bước; xem mục dưới.
| −30 | `(short,byte,byte)` | `MenuController.processmenu` | **chọn dòng menu NPC** |

Cách sender mở một gói: `this.<startMessage>((byte) opcode)` rồi ghi tham số —
`SenderOpcodeReader` dựa vào đó để lập bảng.

## Sáu móc ASM

| # | Lớp . method | Chèn gì |
|---|---|---|
| 1 | `ENGINE.showMainMenu()` | thêm dòng `"Auto Đi Buôn"` (action 9) ngay sau `"Auto Nuôi Pet"` |
| 2 | `ENGINE.perform(int,int)` | prologue `if (handleMenuAction(a,b)) return;` |
| 3 | `CANVAS.tickAll()` | trước mỗi `RETURN`: `tick()` |
| 4 | `ENGINE.paintHud(Graphics)` | prologue `paintHud(g)` |
| 5 | `ENGINE.onNpcMenuOpened(short,byte,Vector)` | prologue `if (onNpcMenu(...)) return true;` |
| 6 | `SHOP.<init>` | trước mỗi `RETURN`: `onShopOpened(this)`; đồng thời mở quyền `shop.type` / `shop.items` |

Móc 3 **phải** nằm trong `CANVAS`, không phải `ENGINE.update()` — xem "Phải mở cửa hàng của
Dylan thật". Móc 6 là cách biết cửa hàng đã mở và lấy được chính đối tượng cửa hàng.

Class engine là **version 47** (Java 1.3) nên không có `StackMapTable` — `COMPUTE_MAXS` là đủ,
không cần `COMPUTE_FRAMES`. `TestAutoTradeLoad` nạp lại class đã vá với `-Xverify:all` để chắc.

Patcher **không đụng** `HSOSKYPetFeedSupport.class` (bản trên đĩa đã cũ hơn bản trong jar, ghi
đè là mất `onCanvasUpdate` / `handleModMenuAction` / RMS) và tự kiểm byte-identical sau khi ghi.

## Nhận diện con bò — chỗ đã làm sai một lần

Con bò **không phải player**. Server tạo nó bằng gói **4** với payload
`[byte kind=1][short template=131][short index][short x][short y][byte -1]`
(`client/UseItem.java:102` và `map/MapService.java:247`). Client bắt gói này, thấy `kind == 1`
nên dựng nó bằng lớp **MOB** rồi nhét vào **cùng** danh sách `CHARS.entities` với player và NPC.
Tên chủ (= tên nhân vật) không có trong gói 4; client tự xin ngay bằng gói **7**, và server trả
`Pet_di_buon.name` ở `core/Service.mob_in4:545`.

Bản đầu tìm bò bằng `entity.name == tên nhân vật`, nên **không bao giờ** thấy: với mob,
`ENTITY.name` là **tên mẫu** (`"Bò vàng"`, do client gán lúc dựng mob từ template), còn tên chủ
nằm ở một field **riêng của lớp MOB**. Hậu quả đúng như người chơi gặp: `SPAWN_PET` tưởng chưa
có bò nên dùng Bò vàng lặp vô hạn và không bao giờ sang bước mua ngọc.

**Cạm bẫy tên obfuscate.** `mob.name` và `mob.template` **trùng tên** với hai field khác của
`ENTITY` nhưng khác kiểu (`byte` và `int`). Bắt buộc đọc qua biến kiểu `MOB`; ép về `ENTITY` là
lấy đúng field sai kiểu, và trình biên dịch không hề báo lỗi. `VerifyAutoTradeSymbols` có
`checkMobShadowsEntity` khẳng định MOB tự khai báo hai field đó, và bản build còn được kiểm lại
ở mức bytecode là `GETFIELD` trỏ vào MOB chứ không phải ENTITY.

`cow()` hiện dùng ba tín hiệu, ưu tiên từ trên xuống:

1. `MOB.name` bằng đúng tên nhân vật → chắc chắn là bò của mình.
2. Chưa có tên (gói 7 chưa về) **và** đúng là Bò vàng → nhận tạm. "Đúng là Bò vàng" cũng có hai
   đường độc lập, chỉ cần một cái đúng: `MOB.template == 131` (bảng `mobs`: 127–132 là nhóm thú
   đi buôn Lừa/Lạc đà/Bò, đều `typemove` 14) hoặc `ENTITY.name` chứa `"bò vàng"`. Hai đường để
   không phụ thuộc vào việc id 131 có giống nhau trên mọi server.
3. Con nào **đã** mang tên người khác thì bỏ qua — để không dắt lầm bò của người ta.

`MAX_SPAWN_TRIES = 4` chặn vòng lặp dùng vật phẩm: nếu đã dắt một con rồi thì server trả
`"Bạn đang dắt 1 con rồi!"` và không trừ vật phẩm, nhưng gọi mãi cũng không bao giờ thấy bò
(ví dụ bò đang ở map khác), nên phải dừng và báo.

**Vị trí con bò đọc theo góc nhìn server.** Server đặt `pet.x = player.x` rồi gửi xuống; client
lưu vào ô "điểm đến" (`stepDestX/stepDestY`) và cho mob **đi bộ** tới dần. Đọc `entity.x/y` đang
vẽ sẽ thấy bò tụt lại xa hơn thực tế — đủ để chốt `PET_FOLLOW_SLACK` treo cả chặng dắt bò. Vì
thế `cowX()/cowY()` lấy `stepDest` trước, chỉ lùi về `entity.x/y` khi `stepDest` chưa được đặt.

Công cụ tra ba ký hiệu này theo cấu trúc: `AddMobSymbols.java` (MOB = lớp con trực tiếp của
ENTITY có `static (IIII)V` gọi `CHARS.add(ENTITY)`).

## Hai ràng buộc của server quyết định cách dắt bò

> Còn một ràng buộc thứ ba, chặt hơn cả hai cái dưới đây: NPC đòi bò trong **75px** mới chịu nói
> chuyện. Xem mục "Ràng buộc thứ ba của server".


- `server/src/main/java/map/MapService.java:213` — mỗi **1 giây** bò mới nhảy tới chỗ người
  chơi, và **chỉ khi** `|bò.x − ta.x| < 85` và `|bò.y − ta.y| < 85`. Xa hơn thì bò đứng im.
- `server/src/main/java/client/Player.java:1209` — qua cổng sang map khác, bò chỉ theo nếu
  đang **< 125px**; xa hơn thì bò ở lại map cũ.

Điểm dễ hiểu sai: bò **không** đuổi theo, nó được **đặt đúng vào chỗ ta đang đứng**. Điều kiện
duy nhất cần giữ là: **tại mỗi nhịp của server, khoảng cách < 85px** — mà khoảng cách đó chính là
quãng đường ta đi được trong 1 giây vừa rồi. Nên chỉ cần **đi chậm hơn 85px/giây** là bò luôn bám
được, không cần đọc vị trí bò để quyết định có bước hay không.

| Hằng số | Giá trị | Vai trò |
|---|---|---|
| `HAUL_STEP_PX` | 60 | mỗi lần giao cho engine tối đa bao nhiêu px |
| `HAUL_TICK_MS` | 1000 | và cách nhau bao lâu → **60px/giây**, dưới 85 |
| `PET_LOST_PX` | 80 | quá ngưỡng này mới coi là bò bị tụt, quay đầu lại đón |
| `PET_STEP_OK` | 20 | khi *đang đón* bò thì phải bám sát đến mức này mới tính là xong |

Ràng buộc bắt buộc: **`HAUL_STEP_PX / HAUL_TICK_MS < 85px/giây`**, `PET_LOST_PX > HAUL_STEP_PX`,
`PET_LOST_PX < 85`, `PET_STEP_OK < PET_LOST_PX`. `TestAutoTradeRoute` khẳng định lại cả bốn.

**Chỗ này đã sai hai lần, cùng một kiểu sai:** lấy khoảng cách tới bò làm điều kiện để được bước.

1. Lần đầu: một ngưỡng duy nhất 36px, **nhỏ hơn** một bước 48px → bước nào cũng bị kết luận là
   "bò tụt lại", quay đầu → bám lại → quay đầu, đi không được mét nào.
2. Lần hai: bắt buộc bám sát 20px mới được bước tiếp → khi bò **không** được kéo (sai khu, sai
   `id_map`…) thì đứng im **vĩnh viễn**, không một dòng thông báo nào.

Bài học ghi lại cho lần sau: **chờ có điều kiện mà không có hạn là lỗi, không phải sự thận
trọng.** Nhịp đi giờ chặn bằng *thời gian*, còn khoảng cách tới bò chỉ dùng làm cảnh báo.

### Hộp thông báo xử lý ở một chỗ duy nhất

Trong `tick()`, trước khi vào bảng phân nhánh trạng thái. Trước kia mỗi bước tự lo phần mình,
nên bước `HAUL` — bước không chờ hộp thoại nào — bỏ qua luôn: mua viên cuối xong, hộp
`"Đã mua 1 Kim cương"` của server về **chậm hơn một frame** nên lúc đó đã sang `HAUL`, và nhân
vật đi qua đi lại với cái hộp chặn trên mặt. Kèm theo:

- `BUY_SETTLE_MS` (2,5s): mua viên cuối rồi **chờ** hộp của viên đó về và được đóng hết mới rời
  bước mua.
- `DIALOG_STUCK_MS` (10s): hộp không đóng được thì báo ra một dòng, vẫn tiếp tục thử.

### Tăng tốc cho bò

Sau khi mua xong, bước `BOOST_PET` bấm "Tăng tốc" trong menu của **chính con bò**. Menu này mở
bằng gói `-30` với id **−56** (`MenuController.processmenu`), và server trả về một menu mang
**id khác** — nên bước này *không* đối chiếu id, chỉ cần menu có dòng nhãn `"tăng t"`.

Thứ tự các dòng khác nhau giữa hai bản server (tham chiếu: Thông tin / Bảo hộ / Hồi máu / Tăng
tốc; bản đang chạy: Hành trang / Tăng tốc / Bảo hộ / Hồi máu) nên **bắt buộc khớp theo nhãn**,
không theo chỉ số. Bấm không được thì bỏ qua sau `BOOST_TIMEOUT_MS` (12s) và đi tiếp — đây là
tuỳ chọn, không phải bước bắt buộc.

Đáng chú ý: `MapService.java:216` dùng bán kính bám của bò là **`85 * speed`**, nên trong lúc
tăng tốc bò theo được xa hơn nhiều. Hiện auto **vẫn đi nhịp cũ** khi bật tăng tốc — muốn tận
dụng thì phải nâng `HAUL_STEP_PX` trong đúng cửa sổ tăng tốc và hạ lại khi hết, chưa làm.

Bước này bỏ qua hẳn khi người chơi tắt nút "Tăng tốc" — xem mục dưới.

### Nút "Tăng tốc [ON/OFF]"

Submenu "AUTO ĐI BUÔN" có hai dòng: `Trạng thái` (action `-22`) và `Tăng tốc` (action `-23`).

| Tăng tốc | Bước `BOOST_PET` | Số ô mỗi nhịp | Tốc độ thực | Ngưỡng "bò tụt lại" | Hạn chặng dắt bò |
|---|---|---|---|---|---|
| ON (mặc định) | có bấm | `HAUL_STEP_PX / 24` = **2 ô** | 48px/giây | **160px** | 25 phút |
| OFF | bỏ qua, sang thẳng `HAUL` | **1 ô** | 24px/giây | 80px | 50 phút |

Ngưỡng "bò tụt lại" **phải** nhân đôi cùng với bước đi. Để nguyên 80px trong khi bước dài 48px
thì vị trí con bò client biết được (server chỉ gửi lại ở nhịp pet, mỗi giây một lần) hay vượt
ngưỡng, và auto cứ đi một tí lại quay đầu về đón trong khi con bò vẫn bám kịp — đúng lỗi người
chơi báo. 160px vẫn thừa biên so với bán kính bám `85 * speed` của server lúc pet được tăng tốc
(`Pet_di_buon.update_speed` đặt `speed = 4` → 340px).

**Nhưng ở cổng thì ngược lại: phải chặt hơn.** `Player.java:1209` chỉ kéo bò qua cổng khi còn
trong **125px**, và con số 125 này *không* được nhân theo tốc độ pet. Ngưỡng đi đường 160px lớn
hơn 125 nên bắt buộc phải có chốt riêng: vào trong `GATE_NEAR_PX` (200px) quanh cổng mà bò xa hơn
`GATE_PET_PX` (70px) thì quay lại đón trước đã. 70 + một bước 48px = 118 < 125, nên chính bước
cuối bước vào cổng cũng không làm rớt bò. Trước đây không cần chốt này vì ngưỡng đi đường (80px)
vốn đã nhỏ hơn 125.

Cắt theo **ô** chứ không theo pixel, vì `player.path` là mảng các ô — chia đôi ở tầng pixel rồi
mới chia cho 24 sẽ ra số ô khác một nửa. Ô rộng 24px (`TileMap` `public static int = 24`) nên
2 ô → 1 ô đúng bằng một nửa. `haulTiles()` luôn trả về **tối thiểu 1 ô**: giao 0 ô là đứng im,
đúng kiểu lỗi "chờ không có hạn" đã mắc một lần.

Hạn của chặng dắt bò **phải** nhân đôi khi tắt tăng tốc, không thì đi nửa tốc độ là chắc chắn
quá hạn giữa đường.

Lựa chọn này không lưu vào RMS — mở game lại là về ON.

### Trạng thái theo chuyến đặt lại ở một chỗ

`beginTrip()` — gọi đúng **một chỗ**, lúc gọi được con bò mới, vì đó mới thật sự là mốc bắt đầu
một chuyến. Nó đặt lại `gemsBought`, `gemTier`, `buyPending`, `lastDialogText`, `grahamAsked`,
`boostDone`.

Quan trọng với `gemTier`: NPC hồi hàng mỗi giờ, nên **chuyến sau phải thử lại kim cương từ đầu**
chứ không giữ bậc ruby của chuyến trước. Hết thật thì mất một nhịp 4 giây rồi tụt lại — rẻ hơn
nhiều so với việc bỏ lỡ hàng vừa hồi.

Trước đây các biến này được đặt lại rải rác ở `start()` và ở chỗ bán xong, và đã mắc đúng kiểu
lỗi đó: `boostDone` chỉ có trong `start()` nên **từ chuyến thứ hai trở đi không bấm tăng tốc cho
bò nữa**. Thêm một biến theo chuyến mà quên một chỗ là lỗi tái diễn — nên gom về một hàm.

### Bấm menu NPC phải chậm

Server đang chạy chặn spam: hai cấp menu bị chọn cách nhau vài milli giây thì nó trả về
`"Thao tác quá nhanh"` và **không bán được**. Bản tham chiếu `server/` không có thông báo này nên
không đọc được ngưỡng thật — `MENU_DELAY_MS = 1500` là con số chọn rộng cho chắc, cần nới thì sửa
đúng một chỗ.

Vì thế `onNpcMenu` **không bấm ngay** nữa: nó chỉ ghi nhớ (npc, cấp menu, dòng cần chọn) rồi trả
`true` để menu không hiện ra, còn `tick()` mới gửi gói `-30` sau `MENU_DELAY_MS`. `grahamAsked`
cũng chuyển sang đánh dấu lúc **gửi thật**, không phải lúc ghi nhớ.

### Tránh cổng khác trên đường đi

Đi thẳng tới cổng không phải lúc nào cũng được: có map mà đường thẳng đi **ngang qua một cổng
khác**, và server mở cổng ngay khi vào trong 40px quanh nó — thế là bị dắt sang map lạ. Người
chơi báo đúng lỗi này ở map "Cổng vào Hạ giới": phải sang phải một đoạn rồi mới đi lên.

Thay vì ghi cứng toạ độ cho từng map, `computeAim()` nhắm tới một **điểm trung gian**: nếu đường
thẳng không sạch thì đi **một trục trước** — ngang rồi dọc, không được thì dọc rồi ngang — và chỉ
chọn thứ tự nào mà **cả hai chặng** đều tránh được cổng khác. Tới góc rồi thì đoạn còn lại chính
là chặng đã kiểm, nên vòng sau tự động nhắm thẳng tới cổng. Không lối nào sạch thì vẫn đi thẳng,
còn hơn đứng im.

`GATE_AVOID_PX = 60` (ngưỡng của server là 40, chừa biên cho một bước). Phần hình học
(`segmentNearPoint`) là hàm thuần nên `TestAutoTradeRoute` kiểm được trực tiếp, gồm cả việc bước
lấy mẫu 16px đủ mịn để không nhảy qua cổng trên đoạn dài.

### Cửa sổ chờ ngay sau khi đổi map

Qua cổng sang map mới thì client **xoá sạch danh sách thực thể**, còn server chỉ gửi lại con bò ở
**nhịp pet kế tiếp** (`MapService.java:215`, mỗi 1 giây), và bảng cổng của map mới cũng cần một
lát để nạp. Trong cửa sổ đó `mapId()` đã là map mới nhưng dữ liệu thì chưa — **không được kết
luận gì**. Hai lỗi thật đã xảy ra vì kết luận sớm:

- coi là mất bò → quay đầu chạy ngược về cổng map cũ rồi mới quay lại (người chơi thấy nhân vật
  "tự nhiên chạy thẳng đến một vị trí nào đó rồi quay lại chỗ bò");
- coi là map không có cổng → **tắt auto** ngay từ lần tra đầu tiên.

Nên có ba mốc, `TestAutoTradeRoute` khẳng định quan hệ giữa chúng:

| Hằng số | Giá trị | Ý nghĩa |
|---|---|---|
| `MAP_SETTLE_MS` | 3000 | vừa đổi map xong thì chưa tin dữ liệu map — phải **lớn hơn 1 giây** vì đó là nhịp gửi lại con bò |
| `COW_GONE_MS` | 4000 | không thấy bò **liên tục** lâu hơn thế mới tin là bò bị bỏ lại |
| `ROUTE_MISS_MS` | 6000 | không tra ra được đường đi **liên tục** lâu hơn thế mới chịu bỏ cuộc |

Cùng một bài học với chỗ nhịp dắt bò: trạng thái tạm thời không phải là kết luận.

### HUD nói rõ đang vướng cái gì

Dòng HUD giờ là `Auto Đi Buôn: <bước>` cộng một hậu tố khi vòng `tick()` không chạy tới phần
thân: `[dừng: mất kết nối]`, `[chờ đóng hộp thoại]`, `[chờ bấm menu]`, `[chờ trả lời mua]`.

Lý do có nó: lần nào auto "đứng im không làm gì" cũng tốn một vòng đoán mò để tìm ra nhánh nào
đang `return` sớm. Nhìn HUD là biết ngay.

### Bộ canh kẹt

`watchStuck()` theo dõi `player.x/y`: đứng một chỗ quá `STUCK_MS` (8s) trong lúc đang di chuyển
thì báo **một dòng** gồm toạ độ, map, khoảng cách tới bò, có đường đi hay chưa, và có còn ở màn
hình game hay không (`HSOSKYPetFeedSupport.getCurrentScreen()` so với `getGameScreen()` — hai
helper có sẵn trong jar, mỗi cái chỉ đọc một field static của CANVAS), rồi **bỏ đường đi đang
treo** để vòng sau tính lại. Vừa nói ra được kẹt vì cái gì, vừa tự gỡ.

Tương tự, không tìm được cổng thì báo kèm **danh sách nhãn cổng đang có trên map** thay vì chỉ
nói "không có cổng".

**Bò tụt lại thì quay lại dắt, không dừng auto.** Đứng chờ tại chỗ là vô ích: server chỉ kéo bò
tới khi khoảng cách ≤ 85px, xa hơn thì nó đứng im vĩnh viễn. Nên `goFetchCow()` cắt ngang đường
đang đi rồi chạy hết tốc lực ngược về phía bò; bám lại được thì báo và đi tiếp. Lúc đang đón thì
phải bám tới `PET_STEP_OK` mới tính là xong (trễ hơn ngưỡng phát hiện) — không có phần trễ này
thì lại quay đầu ↔ bám lại liên tục.

**Bò bị bỏ lại map trước thì quay về map đó đón.** `prevMapId` được cập nhật trong `tick()`,
**không** phải trong `routeTowards`: khi mất bò thì `stepHaul` rẽ sang `fetchCowFromPrevMap` và
không bao giờ gọi `routeTowards` nữa, nên đặt ở đó thì `prevMapId` vĩnh viễn là −1 và tính năng
dừng ngay với lý do "không tìm thấy để quay lại đón" — đúng lỗi đã gặp.
`fetchCowFromPrevMap()` dùng `prevMapId`; không thấy bò trên map hiện tại thì định tuyến ngược về map cũ (đi nhanh, vì
đang không dắt gì). Chỉ khi đã về tới nơi mà quá `COW_SEARCH_MS` (8s) vẫn không thấy thì mới
chịu là mất thật và dừng.

Nhờ ngưỡng 36px ở trên nên **không cần chốt riêng ở cổng**: lúc chạm cổng bò luôn trong 84px,
thoả điều kiện 125px mà server đòi để kéo bò qua map.

**Không cần tự chuyển khu.** Bò vàng chỉ dùng được trong khu đi buôn (`zone_id == maxzone`,
`UseItem.java:87`), nhưng `Player.change_map` đã tự đẩy người mặc áo thương nhân vào
`mbuffer[maxzone]` mỗi lần qua cổng (`Player.java:1190`). Nên chỉ cần mặc áo là tới map 52 đã
đứng đúng khu — đó cũng là lý do mod không đụng gì tới opcode chuyển khu.

## Class version: jar phải chạy được trên JRE cũ

`HSOSKYPetFeedSupport.class` trong cả chuỗi jar (kể cả `HSOLUNA_TRUONGBK_PERFECT.jar`) mang
**class version 61 (Java 17)** từ một lần build cũ. Hậu quả: jar **chỉ** chạy được trên JRE ≥ 17;
mở bằng giả lập chạy Java 8 là `UnsupportedClassVersionError ... only recognizes class file
versions up to 52.0` và không vào được game. Các class khác trong jar đều là 47/51/52.

`AutoTradePatcher` hạ nó xuống **51**, và chỉ đổi đúng **hai byte version** — không phải biên
dịch lại, nên quy tắc "không được build lại `HSOSKYPetFeedSupport` từ source trên đĩa" vẫn giữ.
Verify khẳng định mọi byte khác y nguyên (thực tế chỉ khác **byte thứ 7**).

Trước khi hạ, patcher kiểm class đó không dùng thứ gì của Java 9+ — `invokedynamic`,
constant-dynamic, nest/record/sealed, gọi static/default trên interface — và **báo lỗi** thay vì
hạ bừa nếu có. Class này chỉ dùng `String`, `Math`, `System.currentTimeMillis` và reflection, nên
an toàn.

## Gọi bò: dùng lại vật phẩm là HUỶ con đang có

Trên server đang chạy, dùng lại Bò vàng khi đã có bò thì **huỷ** con đang có — đó chính là cách
bước `DISMISS_COW` hoạt động. Hệ quả mà bản đầu không lường:

1. Auto dùng vật phẩm.
2. Server lag, hộp xác nhận về chậm (người chơi đo được ~0.5 giây).
3. Bò đã được tạo rồi mà client chưa kịp biết.
4. Auto thấy `cow() == null` nên **bấm lại** → *huỷ mất con vừa tạo*.
5. Dylan báo *"Ta không thấy con vật đi buôn của ngươi"* — và lúc đó **không hề có con bò nào**,
   chứ không phải bò đứng xa.

Nên cả chuỗi gọi/huỷ bò chạy theo nhịp chậm và chắc tay:

| Chốt | Giá trị | Vì sao |
|---|---|---|
| `SPAWN_STEP_MS` | 1s | chờ sau khi hộp thoại vừa đóng, và chờ trước khi bấm OK cho hộp xác nhận — bấm ngay khung hình đầu tiên là dễ dính đúng lúc hộp vừa dựng xong |
| `SPAWN_WAIT_MS` | 8s | dùng vật phẩm xong phải đợi chừng này mới dám thử lại. Thử lại sớm là huỷ mất bò |

Chỉ hai bước `SPAWN_PET` và `DISMISS_COW` bấm chậm như vậy; các bước khác giữ nhịp cũ.

## NPC đòi bò trong 75px — biết nhưng không chốt

`MenuController.java:1458` (Dylan) và `:1282`/`:1369` (Graham) đòi bò trong **75px** thì mới nói
chuyện. Auto **không** chốt điều kiện này: đã thử và người chơi yêu cầu bỏ, vì lỗi *"Ta không thấy
con vật đi buôn của ngươi"* thực tế là do **chưa hề gọi được bò** (xem mục trên) chứ không phải bò
đứng xa. Trên đường dắt, server kéo bò về chỗ mình mỗi giây nên nó gần như luôn sát bên.

Nếu về sau gặp lại đúng hộp thoại đó **mà nhìn thấy con bò ở ngay cạnh**, thì lúc ấy mới là lỗi
khoảng cách, và chốt cũ nằm trong lịch sử git.

Hai ràng buộc đã biết là bán kính bám `85 * speed` và cổng `125px`. Còn một cái nữa, chặt hơn cả
hai, mà bản đầu không hề tính tới:

```java
// MenuController.java:1458 — Mr Dylan;  :1282 và :1369 — Graham
if (conn.p.pet_di_buon != null && Math.abs(pet.x - p.x) < 75 && Math.abs(pet.y - p.y) < 75)
    ...
else
    Service.send_notice_box(conn, "Ta không thấy con vật đi buôn của ngươi");
```

**75px, cả hai trục, và không được nhân theo tốc độ pet.** Đây là ngưỡng chặt nhất trong cả vòng
đi buôn — chặt hơn cả ngưỡng dắt bò (80px, hoặc 160px khi bật tăng tốc). Nghĩa là dắt bò tới nơi
xong **vẫn có thể** không nói chuyện được với NPC.

Nên trước khi mở menu Dylan và trước khi bán cho Graham, `cowNearEnoughForNpc()` bắt bò phải
trong `NPC_PET_PX` (60px) đã; chưa đạt thì đi về phía bò vài bước. Chỉ cần đi vài bước là đủ —
server kéo bò về chỗ mình mỗi giây một lần khi còn trong 85px; xa hơn 85px thì bắt buộc phải tự
đi tới, chờ tại chỗ là chờ vô ích.

Lưu ý: gói **mua** (`Service.buy_item`) *không* kiểm khoảng cách bò — nó chỉ đòi NPC −57 trong
100px. Chỉ việc **mở menu** mới đòi 75px. Nên lỗi này biểu hiện đúng một kiểu: đứng cạnh Dylan mà
hộp "Ta không thấy con vật đi buôn của ngươi" hiện lên, cửa hàng không mở.

## Mất bò một nhịp không phải là mất bò

Danh sách thực thể của client có lúc trống một nhịp — mob được gửi lại, vừa đổi khu, tên chủ về
chậm. Ba bước đứng yên (`TALK_DYLAN`, `BUY`, `BOOST_PET`) trước đây tắt auto **ngay lập tức** khi
một khung hình đọc không ra bò, nên thỉnh thoảng vừa báo *"Đã gọi được bò vàng"* xong là báo
*"Mất con bò"* ngay dòng sau.

Giờ cả ba đi qua `cowVanished()`, chờ hết `COW_GONE_MS` rồi mới kết luận — và khi đó:

| Tình huống | Xử lý |
|---|---|
| chưa mua được viên nào | gọi lại con bò mới (tối đa `MAX_RESPAWN` = 2 lần một chuyến) |
| đã mua được ngọc | dừng, vì ngọc đi theo bò luôn, không cứu được |

`AutoTradePatcher` kiểm trong bytecode: `cowVanished()` phải gọi `cowReallyGone()`, và
`stepTalkDylan`/`stepBoostPet` không được gọi thẳng `stop()`.

### Mất bò thì làm lại chuyến, không tắt auto

Server bảo trì **hai lần mỗi ngày**, và sau bảo trì con bò biến mất. Tắt auto khi mất bò nghĩa là
mỗi lần bảo trì xong nó đứng im cho tới khi người chơi để ý — nên mất bò giờ luôn là *làm lại
chuyến*:

| Đang ở bước | Mất bò thì |
|---|---|
| tới chỗ Dylan / mua ngọc / tăng tốc / chờ nhập hàng | gọi con mới ngay tại chỗ (đang đứng cạnh Dylan rồi) → `SPAWN_PET`, mua lại từ đầu |
| dắt bò / bán cho Graham | thử quay lại map trước đón (nếu biết nó ở đâu), không thấy thì về Cổng địa ngục làm lại → `GOTO_START` |

Số ngọc đang trên lưng bò thì mất thật, không cứu được — chuyến sau chạy bình thường.

Vẫn có trần: `MAX_RESPAWN` (2) lần mất bò **liên tiếp mà không đi trọn được chuyến nào** thì mới
dừng, để không đốt sạch Bò vàng trong hành trang. Bộ đếm này được xoá trong `beginTrip()`, tức
mỗi lần gọi được bò và đi được một chuyến là tính lại từ đầu.

### Vào lại game sau khi rớt mạng

`resumeOnline()` quên luôn `prevMapId`. Nếu vào lại mà không thấy bò thì **không được** đoán là
"nó bị bỏ lại map vừa rồi" rồi chạy ngược về đó — cái mốc ấy là từ trước lúc rớt mạng, không còn
nghĩa gì.

### Vơ nhầm bò của người khác

Ở Cổng địa ngục lúc nào cũng có người khác đặt bò. Bò của họ cũng có lúc chưa kịp mang tên chủ,
và `cow()` cũ vơ luôn con đó — một lát sau nó nhận tên chủ người ta thì auto báo "mất bò".

Hai lớp chặn:

1. Con chưa có tên chủ chỉ được nhận nếu **ở trong tầm tay** (`petLostPx()`), và nếu có nhiều con
   thì lấy con **gần nhất**. Bò của mình được server đặt đúng vào chỗ mình đứng
   (`UseItem.java:97`) rồi mỗi giây lại kéo về, nên nó luôn sát mình; bò của người khác ở cạnh
   người ta.
2. Nhận dạng được rồi thì **bám lấy chính đối tượng đó** (`cowRef`) chừng nào nó còn trong danh
   sách. Danh sách được dựng lại mỗi khi đổi map hoặc vào lại game, lúc đó mới nhận dạng lại.

Dòng chat lúc gọi bò ghi rõ nhận dạng bằng đường nào — *(khớp tên)* hay *(chưa có tên chủ)* — để
lần sau có lỗi thì biết ngay do đâu.

## Hết hàng giữa lúc đang mua

Thứ tự ưu tiên: **Kim cương > Ruby > Saphia**.

Chỗ này có một cái bẫy đã làm auto mua hụt cả chuyến:

- Hết hàng **trước khi** mở cửa hàng → NPC bỏ hẳn món đó khỏi danh sách, `gemIdInShop` không
  thấy → tụt bậc sang loại sau. Chạy đúng.
- Hết hàng **giữa lúc** cửa hàng đang mở → danh sách vẫn liệt kê đủ, server **không gửi hộp
  thoại nào**, nó chỉ chạy một dòng chữ ở đầu màn hình: *"Vật phẩm hiện tại đã bán hết"*. Auto
  không đọc dòng đó nên rơi vào nhánh "không có hộp trả lời ⇒ coi như mua được", đếm nhầm và cứ
  bấm mua mãi một loại đã hết.

Nên **không được** dựa vào danh sách hàng để biết còn hàng hay không. Phải đọc dòng thông báo.

### Client có BA kênh thông báo tạm thời, không phải một

Đây là chỗ đã làm hỏng lần sửa đầu tiên. Cùng một đối tượng giữ ba field chữ cho ba kênh:

| Kênh | Đường đi |
|---|---|
| hàng đợi chữ chạy | `showInfo()` → `queue.addElement` → chỗ tiêu thụ gán vào field chữ #1 |
| hàng đợi thứ hai | dòng quá dài (rộng > 140px) → field chữ #2 |
| **dòng ngắn hiện thẳng** | không qua hàng đợi nào, gán thẳng vào field chữ #3 |

*"Vật phẩm hiện tại đã bán hết"* đi vào **kênh ngắn**. Lần đầu tôi chỉ đọc kênh chữ chạy nên
không bao giờ thấy nó. `pollInfo()` giờ đọc cả ba, mỗi kênh một ô nhớ tham chiếu riêng, và
`AutoTradePatcher` **kiểm trong bytecode** rằng `pollInfo()` đọc đúng 3 field chữ của lớp đó.

### Lưới an toàn không phụ thuộc chữ nghĩa

Mua được thì server **luôn** trả về hộp `"Đã mua 1 ..."`. Trước đây khi không có hộp nào,
`settleBuy()` coi như *mua được* — nghĩ là phòng trường hợp server không báo gì. Nhưng đúng lúc
hết hàng thật thì chính nhánh đó làm auto đếm khống và bấm mua đến hết giờ.

Giờ: không hồi âm **`MAX_NO_REPLY`** (2) lần liên tiếp thì chuyển loại. Cho 2 lần chứ không phải
1 để một gói tin về chậm không bị tính oan.

### Đọc dòng thông báo tạm thời

`AddInfoSymbols` tra bốn ký hiệu, hoàn toàn theo cấu trúc: lấy `HSOSKYPetFeedSupport.showInfo`
làm mốc → hàm `showInfo` của client → `GETSTATIC` kiểu Vector trong hàm đó chính là **hàng đợi
thông báo** → chỗ tiêu thụ hàng đợi có dạng `holder.text = (String) queue.elementAt(0)`, từ đó ra
lớp holder và field `String` đang hiển thị.

`pollInfo()` đọc field đó mỗi frame và so bằng **tham chiếu**, không bằng nội dung: hai lần báo
hết hàng liên tiếp có nội dung y hệt nhau, so bằng `equals` là lần thứ hai bị nuốt mất.

`settleBuy()` xét dòng thông báo **trước** hộp thoại, vì nhánh "không có hộp trả lời ⇒ coi như
mua được" sẽ nuốt mất tín hiệu hết hàng nếu xét sau.

### Hết cả 3 loại

| Đã mua được | Xử lý |
|---|---|
| ≥ 1 viên | dắt đi bán luôn. Bò chỉ sống 30 phút — chờ tiếp là mang số ngọc đang có trên lưng nó ra đánh cược |
| 0 viên | sang `WAIT_STOCK`: đứng cạnh Dylan, **mỗi 15 giây vào hỏi cửa hàng một lần** |

Hỏi = mở cửa hàng rồi **thử mua thật**, chứ không nhìn danh sách — lý do ở trên.

Đồng hồ này gắn theo **từng con bò**, không cộng dồn qua các chuyến: `cowSummonedAt` được đặt
ở đúng một chỗ — `stepSpawnPet` khi thấy con bò mới — nên mỗi lần gọi bò là một mốc mới, kể cả
con gọi lại sau khi huỷ. Và nó **không** được dời khi vào lại game sau lúc rớt mạng (khác hẳn
`stateSince`/`fetchSince`): đồng hồ 30 phút của server chạy theo giờ thực, người chơi offline thì
nó vẫn chạy, dời mốc là tự lừa mình rằng bò còn nhiều thời gian hơn thực tế.

Quá `COW_RESUMMON_AGE_MS` (20 phút) kể từ lúc gọi bò mà vẫn chưa mua được gì thì sang
`DISMISS_COW`: dùng lại chính vật phẩm Bò vàng để huỷ con đang có, rồi `SPAWN_PET` gọi con mới —
đồng hồ 30 phút được tính lại từ đầu. Chỉ làm khi trên lưng bò **không có gì**, vì huỷ bò là mất
sạch ngọc đang trên nó. Huỷ không được sau 4 lần thì dừng auto kèm lý do.

> Bản server tham chiếu **không** cho huỷ kiểu này — `UseItem.java:114` trả về *"Bạn đang dắt 1
> con rồi!"*. Người chơi xác nhận server đang chạy thì huỷ được. Nếu về sau không huỷ được nữa,
> đường khác là **tháo áo thương nhân**: `MenuController.java:2456` xoá `pet_di_buon` khi tháo
> `wear[11]`.

## Mất kết nối giữa chuyến

Đây là chỗ đã sai hẳn ở các bản trước: chốt `player() == null || mapHolderMissing()` **không bao
giờ đúng** khi rớt mạng thật. Quét cả jar bằng ASM:

- `CANVAS.mapHolder` chỉ được gán **một lần duy nhất**, trong `CANVAS.<init>`.
- `CHARS.player` không có **một chỗ nào** gán `null`.

Nên nhánh `stop("Mất kết nối.")` cũ chỉ bắt được vài frame trước khi vào game lần đầu; rớt mạng
thật thì auto cứ chạy tiếp vào khoảng không.

### Client làm gì khi rớt

Lớp xử lý dứt kết nối gọi `HSOSKYPetFeedSupport.onDisconnected()` rồi mở hộp **"Bạn bị mất kết
nối với máy chủ."** ở `dialogB`, kèm danh sách nút `[Thử lại, Thoát]`, và hẹn giờ tự đăng nhập
lại bằng `user_pass` đã lưu. Trong lúc chờ thì hiện **"Đang kết nối lại. Vui lòng chờ."** kèm nút
"Đóng".

Có **hai** cơ chế đăng nhập lại, đừng nhầm:

| Cơ chế | Nhịp | Khi nào chạy |
|---|---|---|
| Auto login của bản mod (`HSOSKYPetFeedSupport.onCanvasUpdate`) | **15 giây** (sẵn có) | chỉ khi đã chọn nhân vật auto login |
| Vòng của chính client | 30 giây → **`AutoTradePatcher` hạ xuống 15 giây** | khi auto login không bật |

### Vì sao auto tuyệt đối không được đụng vào hộp thoại hệ thống

`pressOk()` đặt `keyHeld[5]`, mà lớp hộp thoại xử lý phím 5 bằng cách **kích hoạt dòng đang
chọn — mặc định là dòng đầu tiên**. Với hộp mất kết nối, dòng đầu có thể là "Thoát". Tệ hơn:
vòng hẹn giờ tự kết nối lại chạy **trong chính tick của hộp thoại đó**, đóng hộp đi là vòng đó
không chạy nữa. Và bản mod nhận biết trạng thái rớt mạng bằng việc *có* hộp thoại đang mở, nên
`notice()` (có nhánh dự phòng mở hộp thoại) cũng không được gọi lúc này — dùng `noticeQuiet()`.

### Cách xử lý

`offline()` chỉ tạm dừng khi **chứng minh được** là mất kết nối: không còn đọc được nhân
vật/bản đồ, đang ở đúng **màn hình đăng nhập**, hoặc hộp thoại hệ thống về đường truyền đang mở.

**Không bao giờ được dùng "không ở màn hình game" làm dấu hiệu.** Mở cửa hàng của Dylan là **đổi
màn hình thật** — lớp chứa cửa hàng gọi hàm đổi màn hình của client (kiểm bằng ASM: lần theo
ngược các hàm ghi vào field "màn hình hiện tại"). Bản V4 đầu tiên chốt kiểu đó và treo cứng ngay
ở bước "Tới chỗ Dylan": nhánh tạm dừng `return` **trước** chỗ kiểm tra quá hạn nên không có hạn
nào gỡ ra được. `AutoTradePatcher` giờ kiểm ngay trong bytecode rằng `offline()` không gọi
`onGameScreen()`.

Ghi chú cũ trong tài liệu này nói cửa hàng là "popup, màn hình hiện tại không đổi" — **sai**.
Cái đúng là cửa hàng không phải *dialog*, nên `tick()` vẫn chạy (móc nằm trong `CANVAS`, chạy ở
mọi màn hình) và `dialogOpen()` vẫn `false`.

**Bẫy đã tránh:** `HSOSKYPetFeedSupport.isDisconnected` và `isPlayerInGame()` nghe rất đúng tên
nhưng **không dùng được**. Cờ đó chỉ được xoá ở hai chỗ: một nằm trong `if (isPlayerInGame())`
— mà `isPlayerInGame()` trả về `false` ngay khi cờ bật, nên không bao giờ tới —, chỗ còn lại nằm
trong nhánh auto login và chỉ chạy khi người chơi đã chọn nhân vật auto login. Ai không bật auto
login thì cờ bật lên là **dính vĩnh viễn**, và auto đi buôn sẽ treo mãi mãi.

Mất kết nối → `holdOffline()`: dừng sạch, không bấm phím, không gửi gói. Vào lại game →
`resumeOnline()`: **đi tiếp từ đúng bước đang làm**, vì con bò vẫn còn đó (server giữ thú đi
buôn theo nhân vật chứ không theo phiên).

Mọi mốc thời gian phải được dời đúng bằng thời gian nghỉ (`stateSince += away`), không thì hạn
của bước đang làm đã trôi qua và auto tắt ngay khi vừa vào lại. Ngoài ra coi như vừa đổi map:
`mapChangedAt`, `cowGoneSince`, `routeMissSince`, `pathFails`, bộ canh kẹt đều đặt lại.

Ngoại lệ duy nhất là bước `BUY`: màn hình cửa hàng chắc chắn đã đóng, nên lùi một bước về
`TALK_DYLAN` để mở lại — `gemsBought` giữ nguyên nên mua tiếp cho đủ.

## Vài quyết định thiết kế

**Phải mở cửa hàng của Dylan thật.** Bản tham chiếu `server/` chỉ kiểm tra khoảng cách tới NPC
`-57` (< 100px) khi mua (`Service.buy_item:1618-1642`), nên bản đầu chỉ đứng gần rồi bắn thẳng
gói 24 — và **không mua được** trên server đang chạy. Người chơi xác nhận bằng cách chơi thử:
không bấm vào Dylan thì không có viên kim cương nào.

Gói cửa hàng (opcode 23, `Service.send_box_UI`) mang `UTF tiêu đề` + **`byte loại cửa hàng`** +
danh sách `short id` + `UTF tên`. Client lưu hai thứ đó vào màn hình cửa hàng rồi gói mua mới
lấy ra dùng (`sender.buy(shop.type, goods.id, qty)`) — **không phải hằng số**. Bản đầu bắn
`type = 1`, `id = 3592` theo bản tham chiếu; server đang chạy có 4 loại ngọc nên id lệch, và
`buy_item` rơi vào nhánh cuối, không tìm thấy giá, `return` im lặng.

Vì vậy giờ đúng như người chơi làm: đi tới Dylan → mở menu → bấm "Mua bán" ở cả hai cấp → cửa
hàng mở ra → **đọc `shop.type` và id của viên tên "kim cương" từ chính cửa hàng** → bắn gói mua
→ mua xong thì gọi đúng hàm "quay lại" của màn hình để đóng cửa hàng.

Điều kiện để làm được: **`tick()` phải chạy trên mọi màn hình.** Trước đây nó nằm trong
`ENGINE.update()`, mà method đó chỉ được gọi từ `update()` của lớp màn hình game (đúng một chỗ
gọi, `FindEngineUpdateCaller` xác nhận) — mở cửa hàng là auto đứng im. Đã chuyển móc sang method
chạy mỗi frame của `CANVAS`, cạnh móc `onCanvasUpdate` của `HSOSKYPetFeedSupport`. Kèm theo đó
`tick()` có thêm `ALIVE_GRACE_MS`: chạy trên mọi màn hình thì có frame chưa đọc được nhân vật,
chờ 10 giây rồi mới kết luận là mất kết nối.

Hai field cần đọc (`shop.type`, `shop.items`) là **`private`**. Nên có `MakeAutoTradeCompileJar`
sinh `.autotrade-compile.jar` mở quyền đúng hai field đó để biên dịch, và `AutoTradePatcher` mở
đúng hai field đó trong jar xuất ra — hai bên khớp nhau. Nới quyền không thể đổi kết quả phân
giải field: JVM phân giải theo owner + tên + kiểu ghi thẳng trong lệnh, quyền chỉ kiểm sau đó.

Mặt hàng trong cửa hàng chính là `class.ITEM` (cùng lớp với vật phẩm trong hành trang) và id của
nó là `item.id` — `AddShopSymbols` khẳng định lại điều này, nên không cần ký hiệu riêng.

**Kim cương trước, hết thì Ruby.** NPC chỉ hồi khoảng 200 viên kim cương mỗi giờ mà một chuyến
chỉ mất ~7 phút, nên auto vét sạch hàng. `GEM_NAMES = {"kim cương", "ruby"}`, tụt bậc khi hết.

Hai tín hiệu độc lập để biết đã hết, vì mỗi cái bắt được một tình huống khác nhau:

1. **Không còn trong danh sách hàng** — loại đã hết bị bỏ hẳn khỏi cửa hàng (người chơi thấy chỉ
   còn 3 ô thay vì 4). Bắt được trường hợp *đã* hết từ lúc mở cửa hàng.
2. **Hộp trả lời của server** — danh sách hàng chỉ là ảnh chụp lúc mở cửa hàng, nên nếu hết
   *giữa chừng* thì chỉ hộp thoại mới biết. Mua được thì hộp luôn có mảnh `"mua 1"`
   (`"Đã mua 1 Kim cương"`); thiếu mảnh đó là coi như không mua được loại này nữa.

**Hướng an toàn của tín hiệu 2 là coi như MUA ĐƯỢC**: không có hộp nào về trong `BUY_REPLY_MS`
thì vẫn đếm là thành công. Đây chính là chỗ đã sai một lần theo hướng ngược lại — bản đầu coi
"có hộp thoại hiện lên" là dấu hiệu **bị từ chối** rồi tụt bậc, mà server lại bắn hộp sau **mỗi
lần mua THÀNH CÔNG** (`Service.buy_item:1641`), nên mua đúng 1 kim cương + 1 ruby + 1 saphia rồi
bỏ đi. Suy diễn từ hộp thoại thì phải suy từ **nội dung**, và phải chọn hướng sai-thì-vô-hại.

Nội dung hộp đọc từ `dialog.lines` (`String[]`) của lớp hộp thoại — `AddDialogSymbols` tra theo
cấu trúc: lớp đó là kiểu của `canvas.dialogA`, và field nội dung là `String[]` instance duy nhất
trên nó. Ghi lại ngay trong bộ xử lý hộp thoại chung của `tick()` (nơi đóng hộp), vì lúc
`stepBuy` chạy thì hộp đã đóng rồi.

Mọi chuỗi đối chiếu (`GEM_NAMES`, `BUY_OK_MARK`) **phải viết thường** — `TestAutoTradeRoute` bắt
lỗi nếu không, vì cả tên hàng lẫn nội dung hộp đều được hạ về chữ thường trước khi so.

**Id NPC dùng hằng số, không tìm theo tên.** Server đọc id NPC bằng `readByte`
(`MenuController.request_menu:57`) và chỉ đối chiếu id, nên `NPC_DYLAN_ID = -57` /
`NPC_GRAHAM_ID = -58` là đủ. `npcIdOf()` vẫn ưu tiên id của thực thể thấy được trên map nếu tìm
được theo tên, còn không thì dùng hằng số — bỏ hẳn rủi ro "tên NPC phía client không có".

Với Graham thì vẫn phải qua menu NPC (bán là một mục menu), nhưng menu NPC không đổi màn hình
— đúng cơ chế mà Auto Next Map sẵn có vẫn dùng.

**Đi thật, nhưng đi thẳng.** Đây là chỗ làm sai ở bản đầu và đã phải sửa hai lần.

Ghi thẳng `player.x/y` rồi bắn gói 4 thì nhân vật "nhảy" tới nơi. Server **không** coi đó là đi
qua cổng: `MapService` (khoảng dòng 157–196) chỉ mở cổng khi thấy một chuỗi vị trí hợp lệ — phải
rời điểm vừa đến hơn 45px thì cờ `is_changemap` mới bật, sau đó mới xét "đang trong 40px quanh
một vgo". Kiểu nhảy làm nhân vật **đứng sát mép map mà không bao giờ sang được map bên cạnh**.

Cách đúng, giống cơ chế GhostMod dùng: giao một **đường đi** cho engine rồi để engine tự bước và
tự báo vị trí, y hệt lúc người chơi tự đi.

```
short[] path = straightPath(me.x/tile, me.y/tile, tx/tile, ty/tile, maxTiles);
me.stepDestX = me.x;  me.stepDestY = me.y;
me.path = path;  me.pathCursor = 1;  me.startMove();
```

Nhưng **không gọi pathfinder của engine** — jar đã bật noclip vĩnh viễn nên đường thẳng luôn là
đường ngắn nhất, và `screen.findPath` lại **không** đi qua noclip (`adjustTile` chỉ được chèn vào
một method `(II)I` của TileMap, còn `findPath` đọc mảng va chạm bằng `IALOAD` riêng) nên nó vẫn
vòng tránh tường một cách vô ích. Vì vậy `straightPath()` tự dựng mảng ô theo đường thẳng.

Định dạng mảng phải khớp **đúng** cách engine giải mã (đọc từ vòng đi của `PLAYER`):

```java
byte tileX = (byte)(path[cursor] >> 8);
byte tileY = (byte) path[cursor];
stepDestX  = tileX * tileSize + tileSize / 2;
stepDestY  = tileY * tileSize + tileSize / 2;
++cursor;
```

Rút ra ba ràng buộc:

- Mỗi ô là `(tileX << 8) | (tileY & 0xFF)`, và vì **ép kiểu byte** nên toạ độ ô phải nằm trong
  `0..127` — ngoài khoảng đó `straightPath` trả `null`.
- `player.pathCursor` **là con trỏ chạy trong mảng**, không phải "trạng thái". Engine bắt đầu từ
  1 nên **phần tử 0 phải là ô đang đứng**, và mảng cần ít nhất 2 phần tử.
- Engine **tự gán `path = null` khi đi xong**, nên `path == null` chính là tín hiệu "đã tới nơi".

`player.stepDestX/stepDestY` là **đích của bước hiện tại** (không phải "điểm xuất phát" như tên
đặt nhầm lúc đầu); engine mod đặt chúng bằng vị trí hiện tại trước khi giao đường đi, và mod này
làm y hệt.

**Báo lỗi bằng dòng chat, không dùng hộp thoại.** Hộp thoại phải ấn Enter mới tắt, và tắt xong
là mất luôn — đang chạy auto thì không kịp đọc. Nên mọi thông báo đi qua `notice()` và được ghi
thành một dòng chat (tiền tố `[Auto đi buôn]`), nằm lại trong khung chat để cuộn lại xem.

```
chatbox.addLine(tabName, "[Auto đi buôn] ", text, (byte) 0, false)
```

`chatbox.addLine` **tự tạo thẻ chat mới** nếu tên thẻ không khớp cái nào đang có — nên phải lấy
tên thẻ từ chính `chatbox.tabs` (thẻ đầu tiên) chứ đừng truyền chuỗi tự chế, kẻo sinh ra một thẻ
lạ mà người chơi không thấy. Nếu khung chat chưa sẵn sàng thì rơi về hộp thoại như cũ.

Cách tìm ra nhóm ký hiệu này (`AddChatSymbols`, hoàn toàn theo cấu trúc): CHATBOX là class duy
nhất có method `(String,String,String,byte,boolean)V`; instance của nó là field static duy nhất
mang kiểu đó trên CANVAS; danh sách thẻ là field static kiểu Vector duy nhất trên CHATBOX.

**Chống kẹt ở cổng.** Nếu dừng hẳn ngay trên cổng thì không còn gói vị trí nào để server xét.
Đứng quá `GATE_RETRY_MS` (4s) mà chưa đổi map thì lùi về điểm ngay trước cổng rồi bước vào lại.

**Tự tắt auto xung đột.** Bật đi buôn thì tắt Auto Đánh / Auto Next Map / Auto Bán Rác / Auto
Lên Ngựa (cả 4 đều giành quyền điều khiển di chuyển → sẽ làm lạc bò), giữ nguyên Auto HP/MP và
Auto Hồi Sinh. Tắt đi buôn thì khôi phục lại đúng trạng thái cũ.

**Tra theo tên lúc chạy.** NPC, map, vật phẩm đều khớp theo tên nhận từ server. Id chỉ dùng làm
phương án dự phòng (`GEM_FALLBACK_IDS`), vì `server/` là **một phiên bản khác** với server đang
chơi — bản tham chiếu chỉ có 3 loại ngọc và giới hạn 12 viên, còn server thật có 4 loại và 24
viên.

## Dữ liệu game đã đối chiếu

Nguồn: `server/hsoodtest.sql` + `server/src/main/java/core/MenuController.java`.

| Thứ | Giá trị |
|---|---|
| Map bắt đầu | **52 = "Cổng địa ngục"**, Mr Dylan là NPC `-57` tại (384,432) |
| Map bán | **8 = "Thung Lũng Kỳ Bí"**, Graham |
| Vật phẩm pet | item4 **id 84 = "Bò vàng"**, mô tả "Dùng để đi buôn" |
| Áo thương nhân | `wear[11]` = item3 3599 "Thương nhân tập sự" — server tự chặn, mod không kiểm |
| Ngọc | 3590 Saphia · 3591 Ruby · 3592 Kim cương (bản tham chiếu chưa có Ngọc trai đen) |
| Mob con bò | bảng `mobs` **id 131 = "Bò vàng"**, `typemove` 14 (127–132 đều là thú đi buôn) |

Tuyến 52 → 8 dài **14 lần chuyển map**:
`52 → 45 → 44 → 43 → 42 → 22 → 24 → 23 → 20 → 34 → 33 → 25 → 18 → 7 → 8`
(`TestAutoTradeRoute` khẳng định lại tuyến này). Đồ thị cổng và bảng tên map trong
`HSOAutoTradeSupport.java.in` trích từ bảng `maps` của SQL. Bảng đồ thị của engine mod **không
có map 52**, nên tính năng dùng đồ thị riêng chứ không sửa đồ thị của engine.

## Cần kiểm chứng bằng chơi thật

Những chỗ suy ra từ bản server tham chiếu, chưa xác nhận trên server đang chơi:

1. `MENU_DELAY_MS = 1500` có đủ chậm không (ngưỡng thật của server chưa biết).
2. Tuyến 14 map — nếu khu đi buôn có cổng tắt riêng thì rút gọn `MAP_EDGES`.
3. Nhãn `"mua b"` của menu Dylan ở **cả hai cấp** (ảnh người chơi cho thấy cấp 1 có
   "Mua bán"/"Bóng", cấp 2 chỉ có "Mua bán" — nên khớp được).
4. Id NPC `-57` / `-58` trên server đang chạy (lấy từ bản tham chiếu; `npcIdOf()` vẫn ưu tiên id
   đọc được từ thực thể trên map nếu tìm thấy theo tên).
5. Việc đóng cửa hàng bằng `screen.close` có sạch không — đây là đúng hàm mà chính client gọi
   trong nhánh "quay lại", nhưng chưa chạy thử. Cửa hàng **không phải dialog** (nên `tick()` vẫn
   chạy) nhưng **có đổi màn hình hiện tại** — xem "Mất kết nối giữa chuyến". Vì thế
   `watchStuck()` sẽ kèm chú thích "KHÔNG ở màn hình game" trong lúc cửa hàng đang mở; đó là
   bình thường, không phải lỗi.
6. Số ô của con bò: bản tham chiếu chặn ở 12 (`"Nặng quá!!!"`), server đang chạy cho 24. Mua
   quá số ô thì server chặn **trước khi** trừ vàng nên không mất gì. Hạ xuống 1 là cách chạy thử
   cả chuyến mà không tốn vàng. Đổi giá trị thì phải sửa **cả** `EXPECT_MAX_GEMS` trong
   `TestAutoTradeRoute` — test chốt hai chỗ khớp nhau để không ai quên. Từ V6 trở lại `24`, sau
   khi V5 chạy thử 1 viên thông cả vòng.

### Đã sửa sau khi chơi thật

**09/09/2026 — auto vét sạch kim cương của NPC.** ~200 viên/giờ mà một chuyến chỉ ~7 phút. Thêm
tụt bậc sang Ruby khi hết. Xem "Kim cương trước, hết thì Ruby".

**09/09/2026 — ba lỗi sau khi chạy thông cả vòng:**

| Người chơi báo | Nguyên nhân |
|---|---|
| từ chuyến 2 trở đi không tăng tốc lại cho bò | `boostDone` chỉ đặt lại ở `start()`. Bán xong là server thu con bò, chuyến sau là **con khác** nên phải bấm lại — giờ đặt lại ngay khi gọi được bò mới |
| đang dắt bò, tới map mới thì chạy thẳng đi đâu đó rồi quay lại chỗ bò | kết luận "mất bò" ngay trong cửa sổ đổi map → quay đầu về cổng map cũ. Xem "Cửa sổ chờ ngay sau khi đổi map" |
| tới map mới thì báo "không có đường đi tới map y" | cùng cửa sổ đó: bảng cổng chưa nạp xong mà đã tắt auto ngay lần tra đầu |

**12/09/2026 — mất bò là tắt auto.** Server bảo trì 2 lần/ngày, sau bảo trì bò biến mất, nên
auto đứng im cả buổi. Đổi thành làm lại chuyến. Xem "Mất bò thì làm lại chuyến, không tắt auto".

**12/09/2026 — "Ta không thấy con vật đi buôn của ngươi".** Tôi đoán đầu tiên là bò đứng xa quá
ngưỡng 75px của NPC — **sai**. Người chơi xác nhận lúc đó *chưa hề gọi được bò*: server lag, hộp
xác nhận về chậm ~0.5 giây, auto bấm lại và **huỷ mất** con vừa tạo (dùng lại vật phẩm = huỷ).
Đã giãn nhịp cả chuỗi gọi/huỷ bò. Xem "Gọi bò: dùng lại vật phẩm là HUỶ con đang có". Chốt 75px
vẫn giữ vì đó là ràng buộc có thật trong mã nguồn, chỉ là không phải thủ phạm lần này.

**12/09/2026 — vừa gọi được bò xong là báo mất bò.** Ba bước đứng yên kết luận mất bò ngay từ một
khung hình, và `cow()` vơ nhầm bò chưa kịp mang tên chủ của người khác. Xem "Mất bò một nhịp
không phải là mất bò".

**11/09/2026 — sửa lần 1 chưa ăn thua, vẫn spam mua.** Đọc nhầm kênh: client có ba kênh thông
báo tạm thời, dòng "đã bán hết" đi vào kênh ngắn. Đã đọc cả ba + thêm lưới an toàn `MAX_NO_REPLY`
không phụ thuộc chữ nghĩa. Cùng lượt: `AutoTradePatcher` giờ **từ chối build khi `.class` cũ hơn
`.java`** — lần javac hỏng đó đã âm thầm đóng gói bản cũ vào jar mới.

**11/09/2026 — hết hàng giữa lúc đang mua thì auto cứ bấm mua mãi.** Server báo bằng dòng chữ
chạy ở đầu màn hình chứ không bằng hộp thoại. Đã đọc dòng đó (`AddInfoSymbols` + `pollInfo`),
thêm Saphia vào danh sách ưu tiên, và thêm hai bước `WAIT_STOCK` / `DISMISS_COW`. Xem "Hết hàng
giữa lúc đang mua".

**10/09/2026 — bật tăng tốc thì cứ đi một tí lại quay đầu đón bò.** Bước đi nhân đôi nhưng
ngưỡng "bò tụt lại" vẫn 80px. Đã cho ngưỡng nhân đôi theo, kèm chốt riêng ở cổng vì ngưỡng 125px
của server không nhân theo tốc độ pet. Xem 'Nút "Tăng tốc [ON/OFF]"'.

**10/09/2026 — `UnsupportedClassVersionError` khi mở bằng giả lập chạy Java 8.** Không phải lỗi
mới: `HSOSKYPetFeedSupport.class` mang version 61 từ lâu, trước giờ chạy được là nhờ JRE mới. Xem
"Class version: jar phải chạy được trên JRE cũ".

**10/09/2026 — V4 treo cứng ngay khi mở cửa hàng của Dylan.** Chốt mất kết nối mới lấy "không
ở màn hình game" làm dấu hiệu, mà mở cửa hàng là đổi màn hình thật. Đã đổi sang chỉ tạm dừng khi
chứng minh được (màn hình đăng nhập / hộp thoại đường truyền), thêm hậu tố lý do lên HUD, và
`AutoTradePatcher` kiểm luôn trong bytecode.

**10/09/2026 — rớt mạng giữa chuyến thì auto làm bậy.** Chốt mất kết nối dựa vào
`player() == null` không bao giờ đúng, và auto bấm phím mềm lên cả hộp thoại hệ thống. Xem
"Mất kết nối giữa chuyến". Cùng lượt: thêm nút "Tăng tốc [ON/OFF]", hạ nhịp tự đăng nhập lại của
client 30s → 15s, và tạm để `MAX_GEMS = 1` để chạy thử.

**09/09/2026 — thêm bước tăng tốc cho bò** theo yêu cầu người chơi, sau khi mua xong. Xem
"Tăng tốc cho bò".

**09/09/2026 — bán đá quý báo "Thao tác quá nhanh".** `onNpcMenu` bấm ngay khi menu mở nên hai
cấp menu cách nhau vài ms. Xem "Bấm menu NPC phải chậm".

**09/09/2026 — bị dắt sang map lạ ở "Cổng vào Hạ giới", và hộp "Đã mua…" chặn suốt chặng dắt
bò.** Hai lỗi độc lập: đường thẳng đi ngang cổng khác, và hộp thoại chỉ được xử lý rải rác trong
từng bước. Xem "Tránh cổng khác trên đường đi" và "Hộp thông báo xử lý ở một chỗ duy nhất".

**09/09/2026 — dắt bò qua map mới là đứng im, không hoạt động.** Ràng buộc "phải bám sát 20px mới
được bước" biến thành chờ vô hạn khi bò không được kéo. Đã đổi sang chặn nhịp bằng thời gian
(60px/giây) và thêm `watchStuck()`. Xem "Hai ràng buộc của server…" ở trên.

**09/09/2026 — không bấm vào Dylan thì không mua được kim cương.** Suy luận "server chỉ cần
đứng gần NPC" lấy từ bản tham chiếu là sai với server đang chạy. Xem "Phải mở cửa hàng của
Dylan thật" ở trên: đã chuyển `tick()` sang móc chạy mọi màn hình, mở cửa hàng thật, và đọc
`shop.type` + id ngọc từ chính cửa hàng.

**09/09/2026 — spawn bò liên tục, không mua ngọc.** `cow()` đọc sai field tên; xem mục
"Nhận diện con bò".

**09/09/2026 — bốn lỗi trong một lượt chơi:**

| Người chơi báo | Nguyên nhân |
|---|---|
| không thấy bấm vào Dylan để mua | *không phải lỗi* — cố tình không mở cửa hàng, xem "Không mở cửa hàng của Dylan". Đã thêm dòng báo chat để thấy được |
| mua 1 kim cương + 1 ruby + 1 saphia | hộp "Mua 1 …" của server bị hiểu là bị từ chối → tụt bậc ngọc. Bỏ hẳn cơ chế tụt bậc |
| mua đúng 3 viên rồi đi | cùng nguyên nhân trên: tụt hết 3 bậc là chuyển sang dắt bò |
| ra map đầu tiên là mất bò, auto tự dừng | ngưỡng 36px nhỏ hơn bước 48px → quay đầu liên tục; và `prevMapId` chỉ đặt trong `routeTowards` nên không bao giờ được đặt |

## Bản song song: dữ liệu lưu tách hẳn (`autotrade_v11.1.jar`)

Để chạy **hai bản cùng lúc** mà không đè tài khoản / cấu hình của nhau:

```powershell
javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradeV11_1Patcher.java
java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AutoTradeV11_1Patcher autotrade_v11.jar autotrade_v11.1.jar
```

Tính năng **không đổi một dòng nào** — 916/920 entry byte-identical, chỉ vá 4 chỗ. Patcher tự
kiểm lại điều đó trên jar đã ghi và từ chối ghi đè output đã tồn tại.

### Cách ly làm hai lớp, vì mỗi giả lập phân vùng RMS một kiểu

| Lớp | Đổi gì | Cứu trường hợp nào |
|---|---|---|
| Định danh suite | `MIDlet-Name`/`MIDlet-1` `HSOLUNA` → `HSOLUNA2`, `MIDlet-Vendor` `Truongbk` → `Truongbk2` | giả lập đúng chuẩn MIDP: RMS khoá theo (vendor, name) |
| Tên record store | thêm tiền tố `B_` vào **mọi** tên store | giả lập gộp RMS chung một chỗ theo tên store (KEmulator và vài bản khác) |

Không làm lớp thứ hai thì trên KEmulator hai jar vẫn dùng chung `user_pass`, và bản này đăng
nhập là bản kia mất tài khoản đã lưu.

### Toàn bộ RMS của jar đi qua đúng ba chỗ

Tra bằng bytecode, không đoán: chỉ **hai** class trong jar khai báo
`javax.microedition.rms.RecordStore` (`TemMidlet`, `HSOSKYPetFeedSupport`), và jar **không**
dùng `FileConnection` — nên ngoài ba chỗ dưới không còn đường lưu trữ nào khác.

| # | Chỗ vá | Chèn gì |
|---|---|---|
| 1 | `TemMidlet.save(String,byte[])` và `.load(String)` | prologue `if (name != null) name = "B_".concat(name);` |
| 2 | lớp tiện ích, hàm "được phép xoá" `(String)Z` | prologue `if (!s.startsWith("B_")) return false; s = s.substring(2);` |
| 3 | `HSOSKYPetFeedSupport` | hằng LDC `"hsosky_cfg"` → `"B_hsosky_cfg"` (2 chỗ) |

Móc 1 nằm ở `TemMidlet` chứ **không** ở lớp tiện ích bọc ngoài: `saveRegion()` (store
`region_Rms`) gọi thẳng `TemMidlet`, bỏ qua lớp bọc. Vá ở lớp bọc là sót đúng chỗ đó.

Móc 2 **bắt buộc phải có**, đây là chỗ dễ mất dữ liệu nhất: `TemMidlet.clearAll()` duyệt
`RecordStore.listRecordStores()` — danh sách **toàn máy**, không phân biệt jar nào — rồi xoá
store nào hàm đó trả `true`. Hàm gốc trả `true` cho mọi tên **không** nằm trong danh sách giữ
lại (`isLowDevice`, `isQty`, `user_pass`, `isIndexPart`, `isIndexServer`). Hậu quả nếu chỉ làm
móc 1: bản mới đăng xuất một cái là xoá sạch store của bản cũ, còn store `B_user_pass` của chính
nó thì cũng bị xoá vì tên có tiền tố không khớp danh sách giữ lại. Chốt theo tiền tố rồi cắt bỏ
tiền tố giải quyết cả hai chiều cùng lúc.

Tiền tố để **ngắn** (2 ký tự) vì tên record store MIDP tối đa 32 ký tự; tên dài nhất trong jar là
`img_data_char_0_0` (17).

### Class version

`TemMidlet` và lớp tiện ích là **version 47** → không có `StackMapTable`, `COMPUTE_MAXS` là đủ
kể cả khi chèn nhánh rẽ. `HSOSKYPetFeedSupport` là **51** nên chỉ được đổi hằng LDC và phải ghi
bằng `ClassWriter(0)` để giữ nguyên frame sẵn có. Patcher khẳng định lại class version không đổi
sau khi ghi, và **từ chối** vá kiểu chèn lệnh vào class ≥ 50.

### Cái này *không* tách

Dữ liệu nằm trên server (nhân vật, đồ, vàng) vẫn là của tài khoản — đăng nhập cùng một tài khoản
ở hai jar thì vẫn là cùng một nhân vật. Tách ở đây chỉ là **dữ liệu lưu trên máy**: tài khoản đã
nhớ, server đã chọn, cấu hình mod.

---

## v17 — vơ nhầm bò người khác, và nhịp gọi bò

### Lỗi: đi gặp Dylan khi *chưa hề* gọi được bò

Triệu chứng người chơi báo: HUD đang ở bước "Tới chỗ Dylan" nhưng chưa gọi bò, và Dylan trả về
*"Ta không thấy con vật đi buôn của ngươi"*.

Nguyên nhân nằm ở gói tin tạo bò của server — `client/UseItem.java:100-108`:

```java
Message m22 = new Message(4);
m22.writer().writeByte(1);
m22.writer().writeShort(131);      // template
m22.writer().writeShort(index);
m22.writer().writeShort(x);
m22.writer().writeShort(y);
m22.writer().writeByte(-1);
```

**Gói này không có tên chủ.** Tên chỉ về ở nhịp kéo pet mỗi giây sau đó
(`MapService.java:236` — `writeUTF(p.pet_di_buon.name)`), và chỉ khi con bò còn trong tầm.
Nên trong khoảng một giây đầu, bò của mọi người trên map đều **không tên** và giống hệt nhau.

Ở Cổng địa ngục lúc nào cũng đông thương nhân. `stepSpawnPet()` kiểm tra `cow() != null`
**trước khi** dùng vật phẩm lần nào, nên chỉ cần một người bên cạnh vừa gọi bò là auto vơ luôn
con đó, báo "Đã gọi được bò vàng" và đi thẳng sang Dylan — trong khi bản thân chưa bấm gọi.

Hai lưới chắn, độc lập nhau:

| Lưới | Cách làm |
|---|---|
| Chặn trước | `markAlienCows()` chụp lại **tham chiếu đối tượng** của mọi con bò chưa có tên đang có mặt, ngay lúc vào bước `SPAWN_PET` (gọi từ `setState`). `cow()` bỏ qua chúng. Bò của mình luôn là đối tượng **mới**, tạo sau lúc chụp, nên không thể trùng. |
| Bắt lại | `stepTalkDylan()` đọc hộp trả lời: có mảnh `"thấy con v"` thì đánh dấu con đang vơ là bò người khác rồi quay về `SPAWN_PET`. Xoá `lastDialogText` ngay trước khi hỏi NPC, nên chỉ kết luận từ hộp trả lời của **chính lần hỏi này**. |

Thêm một nhịp chờ nhỏ: đứng sát Dylan rồi thì chờ tới `PET_NAME_WAIT_MS` (4s) cho tên chủ về
(`cowByName`) trước khi bắt chuyện. Hết hạn thì cứ hỏi — đã có lưới bắt lại ở trên.

### Nhân vật trôi theo quán tính lúc gọi bò

Chỗ này **không phải chỉ là con số**. Mod không điều khiển từng bước chân — `walkTo()` giao
hẳn **cả một đường đi** cho engine (`me.path = path; me.startMove()`) rồi để engine tự đi hết.
Nên "thôi điều khiển" **không phải là "dừng lại"**: nhân vật vẫn trôi tiếp theo hướng cũ cho
hết đoạn đường đã giao. Thêm nữa, `arrived()` chỉ xem khoảng cách chứ **không** xem `path == null`,
nên `stepGotoStart` chuyển sang bước gọi bò ngay cả khi engine đang đi dở.

Hậu quả: bấm Bò vàng giữa lúc đang đi, rồi còn đứng chờ hộp xác nhận thêm nửa giây nữa — con bò
được tạo lệch hẳn ra khỏi chỗ định đặt. Con bò sinh ra ở `conn.p.x/y` của **server**
([UseItem.java:97](../../server/src/main/java/client/UseItem.java#L97)), không phải toạ độ client đang vẽ.

`standStill()` dừng hẳn **cả ba tầng** rồi mới cho bấm:

| Tầng | Lệnh | Nếu thiếu |
|---|---|---|
| Đoạn đường còn lại | `haltWalk(me)` — `path = null` | engine tiếp tục giao bước mới, trôi cả vài ô |
| Bước đang đi dở | `stepDestX/Y = x/y` | còn trôi nốt một ô (~24px) |
| Vị trí server biết | `sender().move(x, y)` | server vẫn tưởng mình ở chỗ cũ → thả bò ở đó |

Rồi chờ `STAND_STILL_MS` = 300ms cho gói báo vị trí tới server. Áp dụng cho **cả** `stepSpawnPet()`
và `stepDismissCow()` — hai chỗ duy nhất dùng vật phẩm Bò vàng.

`AutoTradePatcher` kiểm lại trên **bytecode của jar vừa ghi**: hai hàm đó phải gọi `standStill()`,
và phải gọi **trước** `useItem4` — gọi sau thì dừng lại lúc đó đã muộn.

### `SPAWN_STEP_MS` 1000 → 500

Nhịp này là khoảng dừng trước khi bấm xác nhận hộp thoại gọi/huỷ bò. Để 1 giây thì hộp chắc
chắn về kịp, nhưng nhân vật **vẫn đang chạy theo hướng bấm gần nhất** trong lúc đứng chờ, nên
điểm gọi bò trôi xa hơn ý muốn. Nửa giây vẫn đủ cho hộp về (độ trễ quan sát được ~0,5s).

Nhưng hạ hằng số **chỉ làm quãng trôi ngắn bớt, không hết** — xem mục trên, `standStill()` mới
là chỗ chặn trôi. Hai thứ bổ sung cho nhau chứ không thay nhau. `TestAutoTradeRoute` chốt khoảng hợp lệ là 500..1000ms.

### Vào lại game sau khi mất kết nối

`resumeOnline()` **không lùi bước nữa**. Mỗi bước tự biết phải làm gì, nên chỉ cần một chỗ quyết
định thay vì hai chỗ có thể lệch nhau:

| Bước đang dở | Còn bò | Mất bò |
|---|---|---|
| B1 Về cổng địa ngục · B2 Gọi bò vàng · B3 Tới chỗ Dylan | đi tiếp | đi tiếp |
| B4 Mua ngọc | `stepBuy` thấy `shopScreen == null` → về B3 mở lại cửa hàng, `gemsBought` giữ nguyên | `cowVanished` → B2 |
| B5 Tăng tốc cho bò | bấm tiếp | `cowVanished` → B2 |
| B6 Dắt bò sang thung lũng | dắt tiếp | `fetchCowFromPrevMap`, mà `resumeOnline` đặt `prevMapId = -1` → `restartTrip` → **B1** |
| B7 Bán đá quý · B8 Quay về · B10 Huỷ bò để gọi lại | đi tiếp | đi tiếp |
| B9 Chờ NPC nhập hàng | hỏi tiếp | `cowVanished` → B2 |

Cửa hàng đóng giữa chừng **không còn tắt auto**: mở lại là mua tiếp đúng chỗ đang dở.

### Tên bước trên HUD

`STATE_LABEL` viết tiếng Việt **có dấu**. Font của client vẽ được dấu (chính dòng
`"Auto Đi Buôn: "` đã có dấu), và `TestAutoTradeRoute` chốt lại để không ai bỏ dấu lần nữa.

---

## v18 — ngưỡng "bò tụt lại" lúc tăng tốc: 160 → 200px

`petLostPx()` trước đây là `PET_LOST_PX * 2`. 200 không còn là bội số của 80 nên tách hẳn thành
`PET_LOST_BOOST_PX`.

Ba con số của server, để đối chiếu — **kiểm theo từng trục riêng**, so sánh `<`:

| Tình huống | Điều kiện | Tối đa an toàn |
|---|---|---|
| Bò bám theo, `speed = 4` | `< 85 * speed` — `MapService.java:216` | 339px |
| Bò bám theo, `speed = 1` | `< 85` | 84px |
| Qua cổng sang map khác | `< 125`, **không** nhân theo tốc độ — `Player.java:1209` | 124px |

Điểm dễ hiểu lầm: `speed = 4` **chỉ sống 10 giây** mỗi lần bấm Tăng tốc
(`Pet_di_buon.update_speed` đặt `time_skill = now + 10_000L`), trong khi mod bấm **một lần mỗi
chuyến** và chặng dắt dài ~7 phút. Nên gần hết chặng, bán kính thật là **85**, không phải 340.

Vậy 200 là một **đánh đổi có chủ ý** (người chơi chọn), không phải sơ sót:

- Được: không bao giờ quay đầu oan khi bò chỉ tụt nhịp thời.
- Mất: nếu một cú lag đẩy khoảng cách vượt 85, bò đứng im từ đó mà auto phải đi tới 200 mới
  quay đầu đón — **mất thêm một đoạn đường, không mất bò**, `goFetchCow()` vẫn đón lại được.

Chốt cổng `GATE_PET_PX = 70` **không ăn theo** con số này — 125px của cổng không nhân theo tốc
độ, nên chặng qua map không đổi gì. `TestAutoTradeRoute` chốt lại đúng điều đó.

---

## v19 — đứng yên chờ bò, thay vì quay đầu đi đón

### Đính chính: Tăng tốc sống cả chặng

Mã tham chiếu đặt `time_skill = now + 10_000L` rồi `speed = 4`, và `MapService.java:220-221`
hạ `speed` về 1 khi hết hạn. **Server đang chạy thì không** — người chơi xác nhận bằng cách
chơi thử: Tăng tốc sống **tới khi con bò biến mất**.

Hệ quả: `speed = 4` suốt cả chặng, nên bán kính kéo thật là `85 * 4 = 340px` chứ không phải
85px. Mọi ghi chép trước đây dựa trên "10 giây" đều sai ở chỗ này.

### Lỗi: quay đầu đi đón trong khi bò vẫn theo kịp

Người chơi báo: *"bò vẫn theo kịp, nhưng player thì quay trở lại với con bò, dẫn đến bị mất
thêm một chút thời gian"*. Khung chat hiện `Bò tụt lại, quay lại dắt.` → `Đã dắt lại được bò,
đi tiếp.` lặp đi lặp lại.

Chỗ hay dính nhất là **gần cổng**: ngưỡng ở đó chỉ `GATE_PET_PX = 70px`, mà khoảng cách tới bò
dao lên xuống theo nhịp kéo pet mỗi giây của server, gói tin lại về trễ — nên vượt 70 là
chuyện thường, dù bò vẫn bám tốt. 14 cổng mỗi chuyến, mỗi lần một vòng quay đầu vô ích.

Đi ngược lại đón **đắt gấp đôi**: mất quãng đường đi ngược, rồi mất thêm quãng đường đi lại.
Mà server kéo bò về chỗ mình **mỗi giây** trong bán kính 340px — nên đứng yên một nhịp là bò
tự tới, mất nhiều nhất một giây.

`waitForCow(gap)` thành đường xử lý **mặc định**, `goFetchCow()` tụt xuống làm đường cuối:

| Tình huống | Làm gì |
|---|---|
| `gap` trong `PET_PULL_PX` (300px) | đứng yên chờ, tối đa `PET_WAIT_MS` (4s) |
| chờ hết 4s vẫn không tới | `goFetchCow()` |
| `gap > 300px` | `goFetchCow()` ngay — quá tầm kéo, chờ cũng vô ích |

Đứng chờ phải là dừng **thật**: `waitForCow` gọi `haltWalk` ngay lần đầu, không thì engine đi
nốt đoạn đường đã giao và vẫn trôi ra xa thêm trong lúc gọi là "đứng chờ".

Ba chốt của các con số:
- `PET_PULL_PX` (300) **<** `85 * 4` (340) — còn trong tầm kéo, chừa biên vì đọc vị trí bò
  luôn trễ một nhịp.
- `PET_PULL_PX` (300) **>** `petLostPx()` (200) — không thì vừa chạm ngưỡng đã quá tầm kéo,
  tức là không bao giờ đứng chờ mà lần nào cũng quay đầu.
- `PET_WAIT_MS` (4s) **<** `STUCK_MS` (8s) — không thì đứng chờ bị chính mình báo "Kẹt tại…".

`AutoTradePatcher` kiểm trên bytecode: `stepToward()` và `routeTowards()` đều phải gọi
`waitForCow()`.

### Còn bỏ ngỏ: nhịp đi 48px/giây

`HAUL_STEP_PX = 60` (cắt thành 2 ô = 48px/giây) được chọn để nằm dưới 85px. Biết tầm kéo thật
là 340px thì con số này đang **thắt cổ chai cả chặng** — giới hạn thật giờ là tốc độ chạy của
nhân vật, không phải luật của server. Nâng lên là chuyến ngắn hơn hẳn. Chưa làm vì cần đo thử
từng nấc. Chốt cổng 70px thì **không** được nâng theo: 125px của cổng vẫn không nhân theo tốc độ.


## v20 - Nhận diện bò theo tên chủ

**Lỗi đã kiểm chứng ngoài thực địa.** Hai người cùng dắt bò qua cổng một lúc. Đổi map là client
dựng lại toàn bộ danh sách thực thể rồi mới hỏi lại gói 7 cho từng mob, nên trong khoảng một giây
**mọi** con bò trên map mới đều chưa có tên - kể cả của mình. Auto vồ nhầm con của người kia, đi
theo nó; đến lúc họ dắt qua cổng thì mất bò và auto đứng im. Cả đêm không chuyến nào.

Hai chỗ hở, cả hai đều ở `cow()`:

1. **Chốt đối tượng được xét TRƯỚC tên chủ.** `if (keptAlive) return cowRef;` đứng trên nhánh
   `named`, nên một lần vồ nhầm là dính luôn - kể cả khi bò thật của mình đang đứng ngay cạnh với
   đúng tên mình. Tên về cũng không gỡ được.
2. **Đón mãi không xong là vòng lặp vô tận.** Bò của mình được server kéo về chỗ mình mỗi giây nên
   đón một lần là xong; bò người khác thì đón tới nơi nó lại chạy theo chủ nó, mình lại đón. Nhân
   vật vẫn dịch chuyển nên `watchStuck()` không bắt được, mà nhìn thì đúng là đứng im một chỗ.

Sửa:

- **Tên chủ là quyền cao nhất.** Server gửi kèm tên người chủ trong gói 7 của chính con bò đó
  (`Service.java:546` `writeUTF(temp2.name)`), nên tên khớp là bằng chứng không thể chối cãi:
  không giới hạn khoảng cách, thắng mọi phỏng đoán. Nhánh `named` chuyển lên trên `keptAlive`.
- **Chốt đối tượng chỉ còn giá trị khi tên chủ CHƯA phủ nhận nó.** Chưa có tên thì vẫn bám theo
  con đang vồ (cửa sổ ~1 giây sau mỗi lần đổi map); vừa có tên mà tên đó không phải của mình thì
  `dropCow()` ngay: đánh dấu alien, thả chốt, để `stepHaul` đi đường mất bò bình thường.
- **`MAX_FETCH_RUNS = 3`.** Đếm số lần quay đầu đi đón đúng một con bò; quá thì buông. Chỉ đếm khi
  chưa khớp tên - đã khớp tên thì đón bao nhiêu lần cũng đón. Đây là lưới an toàn cho trường hợp
  tên không bao giờ về: vòng lặp vô tận thành một lần làm lại chuyến.
- `markAlien()` đầy 16 ô thì đẩy con cũ nhất ra thay vì bỏ qua - con vừa buông là con đang đứng
  ngay cạnh, dễ vồ lại nhất, không được quên.

Chốt trong `AutoTradePatcher.verify()`: `cow()` và `goFetchCow()` đều phải gọi `dropCow()`, và
`dropCow()` phải gọi `markAlien()`.


## v21 - Đi đón bò: từng đoạn ngắn, dừng sớm

**Lỗi đã kiểm chứng.** Bò tụt lại, nhân vật quay lại đón; trong lúc quay lại thì con bò đi ngược
về phía mình, hai bên **đi qua mặt nhau**, nhân vật nằm sau con bò. Rồi lại tiến về trước, bò lại
tụt lại đằng sau. Người với bò cứ đổi vị trí cho nhau, không dắt được mét nào.

Cơ chế: `goFetchCow()` gọi `stepToward(..., false)` - chế độ đi nhanh. Chế độ đó giao cho engine
**tới `FAST_MAX_TILES` = 64 ô (~1536px)** một lần, và `stepToward` không xét lại gì cho tới khi
`player.path == null`, tức khi engine đã đi hết đường được giao. Nên dù con bò ra đón giữa đường,
nhân vật vẫn đi nốt cả quãng tới chỗ cũ của nó. Cộng thêm điều kiện kết thúc lượt đón là bám sát
`PET_STEP_OK` = 20px - tức bắt buộc phải đi hết quãng.

Sửa, ba chốt:

- **Ra ngoài phạm vi an toàn là quay lại ngay.** Bỏ `waitForCow()` khỏi `stepToward()`. Đứng yên
  chờ chỉ còn ở **chốt riêng của cổng** (`GATE_PET_PX` = 70px) - ở đó bò vẫn nằm trong tầm an toàn,
  ngưỡng chỉ chặt hơn vì 125px của cổng không nhân theo tốc độ, nên chờ một nhịp là nó tới. Còn khi
  bò đã tụt ra ngoài `petLostPx()` thì chờ là sai: mỗi phần nghìn giây chờ thêm là nó càng xa.
- **`rejoinPx()` = nửa `petLostPx()`** thay cho `PET_STEP_OK`. Nửa còn lại chính là biên: bò phải
  thực sự tụt lại một lần nữa mới kích hoạt đón tiếp, chứ không kích ngay nhịp sau.
- **`goFetchCow()` tự đi**, không gọi lại `stepToward()`: nhắm tới điểm **cách** con bò `rejoinPx()`
  chứ không nhắm vào chính nó, và giao từng đoạn ngắn (`walkTo(..., true)` = nhịp dắt bò) để nhịp
  nào cũng xét lại.
- `MAX_FETCH_RUNS` giờ đếm **cả khi đã khớp tên**, nhưng xoá mỗi khi qua được một cổng (qua cổng là
  tiến độ thật). Hết số mà bò đúng là của mình thì `restartTrip()` - server không kéo được nó (sai
  khu, hết tăng tốc, lệch `id_map`) thì đón kiểu gì cũng thế.

Chốt trong `verify()`: `stepToward()` **không được** gọi `waitForCow()`, `routeTowards()` **phải**
gọi; `goFetchCow()` **không được** gọi `stepToward()` và **phải** dùng `rejoinPx()`.


## v22 - Trả logic dắt bò về đúng hình v11

**Lỗi đã kiểm chứng ở v21:** cả nhân vật lẫn con bò đứng im, không di chuyển; người chơi bấm một phím
di chuyển thì nó chạy tiếp bình thường.

**Dải chết.** v21 cho `goFetchCow()` tự đi và dừng lại khi còn cách bò `rejoinPx()` = nửa `petLostPx()`
= **100px** lúc tăng tốc. Nhưng chốt cổng đòi bò trong **70px**, và nhánh đó gọi thẳng `goFetchCow()`
chứ không qua `stepToward()`. Ở dải **70..100px**:

- chốt cổng: `cowGap() > 70` → "chưa đạt, không được bước vào cổng"
- `goFetchCow()`: `go = gap - 100 <= 0` → "đủ gần rồi, không đi nữa"

Không bên nào phát lệnh đi, và `cowRecovering` đã được đặt `true` nên cũng không ai gỡ. Nhân vật
đứng im vĩnh viễn; bấm một phím là `cowGap()` đổi → thoát dải → chạy tiếp. Đúng triệu chứng.

`waitForCow()` (v19) cùng loại vấn đề: mỗi đường "chờ" là thêm một `return` đặt **ngay trước** chỗ
phát lệnh đi. Riêng nó thì có hạn 4 giây nên tự thoát, nhưng ghép với một hàm đón có thể không
phát lệnh nào thì thành kẹt cứng.

**Quyết định của người chơi:** quay về logic dắt bò của **v11** - "quay lại con bò nhiều nhưng ít ra
nó còn dắt được bò" - và nâng ngưỡng lúc tăng tốc **160 → 240px**.

Đã gỡ bỏ hẳn: `waitForCow()`, `PET_WAIT_MS`, `PET_PULL_PX`, `petWaitSince`, `rejoinPx()`, và đoạn
`goFetchCow()` tự đi. `stepToward()` / `goFetchCow()` / nhánh chốt cổng giờ **giống hệt bản v11**
(đã đối chiếu bằng cách decompile cả hai jar).

Giữ lại từ v20 - đó là lỗi khác, cũng đã kiểm chứng: nhận diện bò theo tên chủ, `dropCow()`, và
bộ đếm `MAX_FETCH_RUNS` **chỉ áp dụng cho con bò chưa khớp tên**. Bò đã khớp tên thì đón bao nhiêu
lần cũng đón, đúng tinh thần v11.

### Luật rút ra (test và verify đều chốt cứng)

> Ngưỡng **kết thúc** một lượt đón phải chặt hơn **mọi** ngưỡng kích hoạt đón.

`PET_STEP_OK` = 20 < `GATE_PET_PX` = 70 < `PET_LOST_PX` = 80 < `PET_LOST_BOOST_PX` = 240. Không hở
dải nào. `verify()` cấm `waitForCow` / `rejoinPx` tồn tại, và bắt `goFetchCow()` phải đi qua
`stepToward()`.

## v23 - Công tắc Noclip

Thêm dòng `Noclip: [ON/OFF]` vào submenu **AUTO ĐI BUÔN** (action `ACT_NOCLIP = -24`).

- **Bật** (mặc định): giữ nguyên hành vi cũ — `walkTo()` dựng đường **thẳng** tới đích
  (`straightPath`), đi xuyên địa hình, vì mọi ô chặn đã bị `HSOSKYRuntimeSupport.adjustTile`
  biến thành ô đi được.
- **Tắt**: `walkTo()` chuyển sang `realPath()` — gọi thẳng pathfinder của engine
  (`screen.findPath`) để lấy đường đi thật, rồi cắt lấy đoạn giao cho nhịp này.

Ba điểm phải để ý:

1. **Thứ tự tham số của `findPath` là ĐÍCH trước, vị trí hiện tại sau.** Đọc từ chính chỗ
   engine gọi nó (nạp toạ độ đối tượng đích, rồi mới nạp `CHARS.player`). Kết quả trả về đã
   theo hướng hiện tại → đích. `realPath()` còn kiểm lại `found[0]` và `found[last]` đúng hai
   đầu mong đợi, sai thì trả `null` chứ không đi bừa.
2. **`PATH_BUDGET = 5000`.** Cả ba chỗ engine gọi `findPath` đều truyền 5000. Bản nháp để 127
   — dễ nhầm với biên toạ độ ô (0..127), nhưng không phải: để quá nhỏ thì map nào đường dài là
   `findPath` trả `null`, `walkTo` báo thất bại, và sau `MAX_PATH_FAILS` lần là auto tự tắt.
   Test chốt cứng con số 5000.
3. **Lựa chọn sống qua lần đổi map.** `HSOSKYRuntimeSupport.onWorld()` đặt lại `noClipEnabled`
   mỗi lần đổi map/khu, nên nếu chỉ đặt cờ đó thì qua cổng đầu tiên là noclip bật lại. Lớp đó
   giữ thêm `requestedNoClipEnabled` — lựa chọn của người chơi — và `onWorld` khôi phục theo
   nó. `setNoClipEnabled()` đặt cả hai.

`.autotrade-compile.jar` giờ **thay `HSOSKYRuntimeSupport` bằng bản vừa biên dịch trên đĩa**,
đúng việc `AutoTradePatcher` làm ở bước [7]. Jar gốc mang bản cũ của lớp đó (chưa có
`setNoClipEnabled`) nên javac báo `cannot find symbol`; và để lệch thì bản biên dịch khác bản
chạy, tức biên dịch được chưa chắc chạy được.

### Bản x3

`RepackSupportX3` thay lớp support ở cả ba gói (mặc định, `b/`, `c/`) của jar x3, tự đổi tên
lớp theo gói. Nó **tự kiểm trước khi ghi**: chạy phép đổi gói lên lớp cũ của chính jar đầu vào
rồi so với lớp `b/` `c/` cũ có sẵn trong đó — trùng nghĩa là phép đổi tên tái tạo đúng những gì
công cụ gốc đã làm.

### Một lần suýt mất sạch

`HSOAutoTradeSupport.java.in`, `AutoTradePatcher.java`, `TestAutoTradeRoute.java` đều **chưa
từng được commit** và có lúc biến mất khỏi đĩa; khôi phục được từ Thùng rác. Từ bản v23 trở đi
toàn bộ nguồn Auto Đi Buôn đã vào git.

Cũng lần đó phát hiện `MAX_GEMS` đang để **1** — giá trị chạy thử của một phiên trước, suýt
phát hành. Test đã chốt cứng 24 chính để bắt việc này.

## v24 - Tắt Noclip thì phải tính đường MỘT LẦN rồi đi theo

**Lỗi đã kiểm chứng:** vừa tắt Noclip là nhân vật chạy vòng vòng, không đi đâu cả, ngay ở
bước đầu tiên (Về cổng địa ngục).

Hai nguyên nhân, cả hai đều là do bê nguyên cách làm của chế độ đi xuyên tường sang.

### 1. Tính lại đường mỗi nhịp

`walkTo()` chỉ giao cho engine một đoạn ngắn rồi nhịp sau tính lại từ vị trí mới. Đi **đường
thẳng** thì tính lại vẫn ra đúng đường đó — vô hại, và đó là lý do nó chạy tốt suốt hai chục
bản. Nhưng **đường đi thật** thì vòng qua chướng ngại, và từ hai vị trí cách nhau vài ô, hàm
tìm đường có thể trả về hai lối vòng khác hẳn nhau (trái hay phải đều dài bằng nhau). Nhịp
này rẽ trái, nhịp sau rẽ phải, rồi lại trái — chạy vòng vô tại chỗ.

`plannedPath()` giữ lại kế hoạch, chỉ tính lại khi: chưa có, đổi ô đích, đổi map, hoặc nhân
vật không còn đứng trên đường đã vạch.

Cả **GhostMod** lẫn **chính engine gốc** đều làm thế — đó là chỗ người chơi chỉ sang xem:
- `GhostMod.assignPlayerPath()`: tính cả đường rồi giao hẳn cho nhân vật, không tính lại.
- Engine gốc: `player.path = findPath(destX, destY, curX, curY, 5000)` rồi đặt
  `pathCursor = 1` và gọi `startMove()`. Xác nhận luôn hai điều: thứ tự tham số **đích trước**,
  và con trỏ bắt đầu từ **1** (phần tử 0 là ô đang đứng) — đúng như `walkTo()` đang làm.

### 2. Nhắm góc để né cổng khác

`computeAim()` neo điểm góc vào **toạ độ hiện tại** của nhân vật: `(gx, my)` hoặc `(mx, gy)`.
Đi đường thẳng thì một trục đứng yên nên góc đó đứng im. Đi đường thật thì đường vòng lên
xuống cả hai trục, nên góc **trượt theo từng bước chân** — đuổi mãi không kịp. Mà tắt Noclip
rồi thì cũng không cần nó nữa: đường thật đã đi theo lối thật, không cắt ngang cổng khác như
đường thẳng. Giờ chỉ nhắm góc khi `noclipOn`.

### 3. Ô đích có thể không đi được

Cổng và các điểm mốc được ghi bằng **toạ độ pixel**, không bảo đảm ô dưới chân chúng là ô đi
được trong bản đồ thật. Bật Noclip thì không sao. Tắt đi thì `findPath` không tới nơi.
`approachPath()` dò vòng tròn dần quanh ô đích (`PATH_APPROACH_TILES = 4`) tìm chỗ đứng thay
thế — GhostMod làm y hệt cho cổng (`PORTAL_APPROACH_RADIUS = 4`).

### Chốt trong verify

- `realPath()` **không được** gọi thẳng `findPath` — phải đi qua `plannedPath()`.
- `plannedPath()` **phải** có `approachPath()`.
- `routeTowards()` **phải** đọc `noclipOn` (tức có canh nhánh cho `computeAim`).

## v25 - Vào đúng cổng thay vì lảng vảng quanh cổng

**Lỗi đã kiểm chứng (tiếp theo v24):** tắt Noclip thì cứ đi đến gần cổng là tự nhiên chạy ra
chỗ khác, không chịu sang map.

Hai nguyên nhân, và cả hai đều do v24 gây ra hoặc để lộ.

### 1. `approachPath()` — dò quanh ô đích lấy chỗ đứng thay thế

Ý tưởng chép từ GhostMod (`PORTAL_APPROACH_RADIUS = 4`) nhưng đặt sai chỗ. Mỗi nhịp nó có thể
chọn **một ô thay thế khác**, nên nhân vật nhảy qua nhảy lại giữa mấy ô quanh cổng. Tệ hơn: ô
thay thế nằm ngoài **40px** mà server đòi để mở cổng, nên dù có đứng yên ở đó cũng không bao
giờ sang map được.

Đã bỏ hẳn. Thay bằng đúng cách **engine gốc** làm: gọi `findPath`, trả về `null` thì **bước
thẳng** tới đích chứ không bỏ cuộc. Chỉ bước thẳng trong phạm vi `STRAIGHT_NUDGE_PX` (= 200px,
tức vùng quanh cổng) để đoạn đi xuyên — nếu có — luôn ngắn, còn cả chặng đường dài vẫn đi theo
lối thật.

Đây cũng là lý do cổng hay là chỗ duy nhất cần bước thẳng: cổng được ghi bằng **toạ độ pixel**,
ô dưới chân nó không chắc là ô đi được trong bản đồ thật.

### 2. `preGateX/preGateY` không bao giờ được xoá khi đổi map

Điểm "lùi ra rồi bước vào lại" (nhánh `gateBackOff`, dùng khi đứng trên cổng 4 giây mà server
không mở) được ghi trên map **cũ**. Qua cổng xong mà gặp lại nhánh đó là nhân vật chạy tới một
toạ độ của map trước — tức lao đi đâu đó giữa map mới. Lỗi này có từ lâu và độc lập với Noclip;
bật Noclip thì qua cổng nhanh nên hiếm khi chạm tới.

### Chốt trong verify

- `walkTo()` phải có **cả hai** đường: `realPath()` và `straightPath()` dự phòng.
- Lớp support **không được** có lại `approachPath()`.

## v26 - Ép bước cuối lên ô cổng (mảnh đã thiếu qua ba bản)

Ba bản liền (v23, v24, v25) đều hỏng cùng một kiểu: tắt Noclip thì đi tới gần cổng rồi lại
chạy ra chỗ khác, không vào cổng. Mỗi lần tôi chữa một triệu chứng khác nhau mà không tìm ra
nguyên nhân gốc.

**Nguyên nhân gốc:** cổng được ghi bằng **toạ độ pixel**, và ô dưới chân nó thường **không
phải ô đi được** trong bản đồ thật (cổng hay nằm sát viền map). Bật Noclip thì không sao — mọi
ô đều thành đi được. Tắt đi thì `findPath` **không bao giờ đặt chân lên ô đó**: hoặc trả về
null, hoặc vòng ra một lối khác. Nên nhân vật cứ lẩn quẩn quanh cổng mà không bao giờ vào
trong **40px** mà server đòi để mở cổng.

Mọi cách chữa trước đều là tìm-đường-kiểu-khác, nên đều thất bại theo cùng một cách.

**GhostMod giải đúng chỗ này** — `startMoveToPixel()`:

```java
if (isNearPosition(targetX, targetY, cs.l)) {   // trong ĐÚNG MỘT Ô
    if (markPortal) return forcePortalStep(targetX, targetY);
```

`forcePortalStep()` không tìm đường: nó đặt thẳng điểm đến rồi giao cho nhân vật một đường đi
**duy nhất một phần tử** — chính ô của cổng — với con trỏ **0**.

`forceStep()` làm y hệt. Con trỏ phải là 0 chứ không phải 1: đường bình thường có phần tử 0 là
ô **đang đứng** nên engine bắt đầu từ 1, còn đường này chỉ có đúng ô đích.

Chỉ ép khi đã trong `GATE_FORCE_TILES = 2` ô, nên đoạn đi thẳng luôn rất ngắn.

Kèm theo: nếu ô đích không đặt chân lên được thì `neighbourPath()` đi tới **ô kề bên** gần
người chơi nhất trong 8 ô kề. Chỉ 8 ô kề chứ không dò vòng bán kính 4 như bản v24 — ô kề cách
ô đích đúng một ô (~24px) nên đứng đó đã nằm trong 40px server đòi, và kế hoạch được nhớ lại
nên giữa chừng không có chỗ nào để đổi ý.

### Chốt trong verify

- `walkTo()` **phải** gọi `forceStep()`.
- `forceStep()` **không được** gọi lại bất kỳ hàm tìm đường nào — thế thì nó hết là bước ép.
- `plannedPath()` **phải** có `neighbourPath()`.
