# HSOSKY — protocol mạng và kỹ thuật mod

Tài liệu này là phần kiến thức **tái dùng được** rút ra từ đợt reverse engineering
HSOSKY. Mọi bảng dưới đây đã đối chiếu trực tiếp với bytecode hoặc với trace thật từ
server, không phải suy đoán. Phần hạ tầng multi-account (mirror rig, clone, probe) đã
được xoá khỏi project; kiến thức protocol thì giữ lại ở đây.

## Khung truyền

| Thành phần | Nội dung |
|---|---|
| Frame gửi | `[opcode:1][len:2][payload:len]` |
| Frame nhận | như trên; riêng opcode `-51/-52/-54/126` dùng len 4 byte, và `126` là prefix của opcode thật |
| Handshake | client gửi opcode `-40` payload rỗng → server trả `-40` với `[keyLen:1][key:keyLen]` |
| Fold key | `for i in 0..len-2: key[i+1] ^= key[i]`, rồi bật cờ mã hoá |
| Keystream | XOR từng byte, **cursor riêng cho mỗi chiều**; cursor là `byte` nên keyLen phải ≤ 127 |
| Login | opcode `1`: 6 string UTF (2 đầu là user/pass), 2 byte cờ, `int` selector (nhân vật), rồi các cờ thiết bị |

Keystream XOR là **theo từng connection** — không thể copy byte đã mã hoá từ phiên này
sang phiên khác. Muốn can thiệp packet thì phải hook ở tầng plaintext, trước lớp mã hoá.

Trên server thật, key trả về dài **4 byte**. Chỗ khó nhất của codec là opcode dùng len 4
byte cùng thứ tự tiêu thụ keystream đảo — đã verify đúng qua hàng chục frame `-52` dài
4–6 KB liên tiếp không lệch.

Login frame thật do client tự serialize, dạng 52 byte:

```
"USERNAME" "PASSWORD" "3.0.1" "0" "0" "0"
01 00 | ffffffff (selector = -1) | ff 00 ff 01 00 | ffff | 0000 ("") | 00
```

`selector = -1` nghĩa là xin danh sách nhân vật thay vì vào thẳng bằng một nhân vật.

## Bảng opcode đã biết

| Opcode | Chiều | Nội dung |
|---|---|---|
| `-40` | cả hai | handshake, server trả keystream |
| `1` | gửi | login (cũng là chọn nhân vật, qua field `selector`) |
| `4` | gửi | **báo cáo** vị trí (2 short) — không phải lệnh đi, xem mục dưới |
| `6` | gửi | đánh quái: `skillIndex`, `targetCount`, `targetId[]` |
| `9` | gửi | đánh người chơi, cấu trúc như `6` |
| `13` | nhận | **danh sách nhân vật**: `[count:1]`, rồi mỗi nhân vật `[id:int][ten:UTF][class:1][2 byte][soDoMac:1][(slot,val)×n][short][3 byte][petId:short]`, nếu `petId>=0` thì thêm `[tenPet:UTF][lvPet:1]` |
| `13` | gửi | **vào game**: `[0:byte][charId:int]` |
| `14` | gửi | **tạo nhân vật**: `[class:byte][ten:UTF][cc:byte][bV:byte][bY:byte]` |
| `31` | gửi | hồi sinh — payload `00` = về làng (miễn phí), `01` = hồi sinh tại chỗ (**tốn ngọc**) |
| `31` | nhận | server echo lại user/pass để client lưu vào RMS |
| `32` | nhận | cập nhật HP/MP tuyệt đối |
| `37` | nhận | thoại lỗi từ server, dạng `[UTF]` — tên nhân vật bị từ chối trả về ở đây |
| `40` | nhận | quái/NPC dùng chiêu |
| `51` | nhận | rời khu vực |
| `54` | gửi | chuyển khu vực |
| `63`, `-57` | nhận | cờ trạng thái sau login |
| `67` | gửi | cường hoá: `(byte, short, byte)` — request mà `HSOSKYAutoEnhancePatcher` hook |
| `79` | gửi | gói Tiện ích: `1` = hỏi trạng thái, `2,n` = mua n gói, `3,idx` = mở tiện ích `idx` (1..13) |
| `80` | nhận | trả lời gói Tiện ích: `1` = `active, int giâyCònLại, short, short giá, byte sốNgày, UTF`; `2` = kết quả mua; `3` = `byte code (0 = từ chối), byte idx, UTF` |
| `-30` | nhận | menu server: `[npcId:short][menuId:byte][soLuaChon:byte][nhãn:UTF ...][tiêu đề:UTF]` |
| `-52` | nhận | server push ảnh/sprite (`createImage` rồi cache theo `"type_id"`), 4–6 KB mỗi frame, đẩy ngay khi vừa kết nối **trước cả login** |
| `16`, `52`, `65`, `3`, `1` | nhận | luồng dữ liệu in-game sau khi vào map |

### Trình tự vào game

1. login với `selector = -1` → server trả opcode `13` là danh sách nhân vật
2. `count == 0` → gửi opcode `14` tạo nhân vật
3. server trả opcode `13` lần nữa → đọc `id` rồi gửi opcode `13` để vào game
4. server bắt đầu đẩy dữ liệu in-game (`16`, `52`, `65`, `3`, `1`)

### Bảng template nhân vật (dựng sẵn trong client, không phải server gửi)

| idx | class | cc | bV | bY |
|---|---|---|---|---|
| 0 | 0 | 0 | 8 | 0 |
| 1 | 1 | 1 | 9 | 0 |
| 2 | 2 | 2 | 10 | 0 |
| 3 | 3 | 3 | 11 | 0 |
| 4 | 5 | 2 | 10 | 0 |
| 5 | 6 | 1 | 9 | 0 |

### Luật đặt tên nhân vật

- Client chỉ kiểm `length >= 6`.
- Server từ chối tên có chữ số, và từ chối cả tên dạng `helload6` / `helloadg` với thông
  báo *"Tên không hợp lệ"*.
- Tên đã có người dùng trả về *"Tên này đã sử dụng, hãy thử lại"*.

## Bài học quan trọng khi mod

### Opcode 4 là báo cáo vị trí, không phải lệnh đi

Chỗ dễ hiểu sai nhất. Đối chiếu dump field của nhân vật với trace cùng thời điểm lúc
người chơi bấm mũi tên: dump `x=549 y=312` ↔ client gửi `02250138` = (549, 312).

Client **tự di chuyển ở phía nó** rồi gửi opcode 4 để báo "tôi đang ở đây". Chỉ gửi
opcode 4 thì nhân vật đứng im — đã thử gửi đủ 5 lần với payload lặp lại y nguyên. Gọi
handler chạm (`pointerPressed`/`pointerReleased`) cũng không đi.

Cách đúng: **đặt toạ độ ở client trước, rồi báo lên server**. Bước đi thật của client
khoảng 63–77px mỗi lần.

### Dò field trong class bị obfuscate

- **Bảng field nhân vật**: bật chế độ dump mọi field `int` vừa đổi giá trị, mỗi giây một
  dòng. Đi vài bước là thấy index nào là toạ độ. Ở bản đã khảo sát: x = index 0,
  y = index 1, HP = index 22. Index này đổi theo bản jar, phải dò lại.
- **Field HP**: quét class cha của nhân vật tìm field `int` vừa được ghi từ `readInt()`
  của packet, vừa bị so với 0 tới 30 lần trong client — ứng viên đúng hơn ứng viên nhì
  gấp 10 lần. Patcher nên yêu cầu khoảng cách tối thiểu gấp 3 mới nhận, tránh nhận sai.
- **Dò class theo hình dạng, không theo tên**: tên class/method đổi mỗi bản jar. Tìm theo
  đặc điểm bytecode (descriptor, hằng số xuất hiện trong method, số call site).

### Hook đặt ở đâu

- `onSend` đặt trên tầng mã hoá (để thấy plaintext).
- `onRecv` đặt trong ctor packet inbound — jar chỉ có **1 call site** cho ctor đó nên
  trace nhận packet không lẫn nhiễu.
- Nút bấm khi chết (`new Command("Về làng", 2, player)`) là **soft-key của client**, không
  phải menu server — đừng đi tìm menu `-30` cho việc đó.

### Cầu nối vào code đã obfuscate

Xem `HSOBridgeApi.java`: patcher dùng ASM tự sinh một class implement interface đó ngay
trong jar, mỗi hàm được sinh thành lời gọi tới đúng method/field đã dò được. Class mod
gọi bằng tên có nghĩa qua `Class.forName(...).newInstance()`. Đổi bản jar thì chỉ patcher
phải dò lại, code mod không sửa. `HSOReviveNorthPatcher.java` là ví dụ đầy đủ.

### Chặn câu chat làm lệnh bật/tắt mod

Hook hàm gửi chat, trả `true` là chặn luôn không gửi lên server — người khác không thấy.
Cách bật/tắt mod tại runtime rẻ nhất, không cần UI. Xem `HSOReviveSupport.java`.

### Mọi lối vào mod phải nuốt Throwable

Mod hỏng thì game vẫn phải chạy. Đây là lý do mọi entry point trong các support class đều
bọc `try { ... } catch (Throwable t) {}`.

## Gói Tiện ích: cổng nằm ở server

Client **không** chặn — panel gửi `79/3/idx` bất kể acc có gói hay không, server trả
`80/3` với `code = 0` để từ chối. Nghĩa là **patch client để tự mở screen là vô ích** với
những mục server có kiểm tra. Đọc phản hồi:

| Phản hồi server | Nghĩa |
|---|---|
| `80/3 code=0` | cổng nằm ở server, patch client vô ích |
| cho mở dù chưa mua | mục đó dùng được miễn phí |
| im lặng | thường là có kiểm tra và không thèm trả lời |
| cắt kết nối | frame đó bị chặn thẳng — đừng lặp |

## Capture trace để dò frame

Đừng đoán payload. Bật tap, làm thao tác đó **một lần qua NPC/UI thật**, rồi đọc dòng `S`
trong trace. Định dạng trace dùng trước đây: `<S|R|C> <timestamp> <opcode> <len> <hex>`.

Lưu ý khi log: **không** ghi payload của opcode `1` (chứa mật khẩu).
