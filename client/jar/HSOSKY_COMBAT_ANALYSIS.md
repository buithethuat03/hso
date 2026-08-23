# Phân tích combat của `HSOSKY.jar`

## Phạm vi và cách đối chiếu

- `HSOSKY.jar` (MIDlet version 111) đã được decompile bằng CFR vào `src-hsosky-cfr/`.
- JAR bị obfuscate rất mạnh: tên lớp, hàm và biến đều bị đổi thành chuỗi `I/l` dài.
- Để đọc được ý nghĩa, các lớp trong JAR hiện tại được ánh xạ với source bản ít obfuscate hơn trong `src-decompiled/` bằng chuỗi hằng duy nhất và cấu trúc hàm. Ví dụ: `java.game.Player vec8`, `ReadMessenge vec5`, `MainMonster VecCatalogyMonSter`, `Att 1` và thứ tự đọc/ghi packet đều trùng.
- Tên dễ đọc dùng dưới đây: `bq` = Player, `fa` = MainObject, `au` = Monster, `dh` = bộ điều khiển đánh/skill, `q` = gửi packet, `er` = nhận packet.

## Kết luận nhanh

| Nội dung | Client làm gì | Dữ liệu quyết định cuối | Kết luận |
|---|---|---|---|
| Hồi chiêu skill | Chặn nút, đếm thời gian và vẽ cooldown | Client nhận thông số skill từ server; JAR không đủ để chứng minh server có từ chối request gửi sớm hay không | Có kiểm tra ở client; kiểm tra server cần source/log server để khẳng định |
| Damage người chơi đánh quái | Chọn skill/target, chạy animation, hiển thị số | Server gửi damage và HP còn lại của từng target | Server-authoritative |
| Damage quái đánh người chơi | Client chạy animation/effect theo packet | Server gửi sự kiện đánh và các packet cập nhật HP tuyệt đối/delta | Server-authoritative |
| HP/MP hiện tại và tối đa | Lưu cache, hiển thị thanh, kiểm tra sơ bộ đủ mana | Server khởi tạo và liên tục gửi lại HP/MP; combat response cũng chứa HP/MP sau đòn | Server-authoritative, client có dự đoán MP để làm UI mượt |
| Bình HP/MP | Client chặn spam khoảng 2 giây và gửi ID vật phẩm | Server trả số lượng vật phẩm và HP/MP mới | Server-authoritative |

## 1. Hồi chiêu khi đánh quái

Thông số của từng cấp skill được server gửi xuống trong packet danh sách skill. Trong `er.D`, client đọc:

- `fd.a`: mana cost;
- `fd.g`: cooldown theo millisecond;
- các trường còn lại: thời lượng/range/effect/số target.

Trước khi dùng skill, `bq.j(skillIndex, hotkeyIndex)` kiểm tra:

1. skill đã học;
2. timer `bq.r[skillIndex].b <= 0`;
3. MP hiện tại `bu` đủ so với `fd.a`;
4. nhân vật đang ở trạng thái cho phép.

Khi animation tới frame ra đòn, `dh.g()` gọi `cn.g.d(actionId, skillIndex)`. Hàm này đặt timer bằng `fd.a()` (thực tế trả `fd.g`, trừ trường hợp có override) và trừ dự đoán mana `bu -= fd.a`. Mỗi tick, client trừ thời gian thực đã trôi qua khỏi timer.

Điểm quan trọng: đây là gate phía client. Request đánh quái chỉ chứa skill index và target IDs, không chứa timestamp cooldown. Vì không có code server trong JAR, không thể kết luận chỉ từ client rằng server có/không kiểm tra cooldown lần nữa. Việc sửa timer client chỉ bỏ chặn giao diện; không chứng minh server sẽ chấp nhận đòn gửi sớm.

## 2. Damage khi người chơi dùng chiêu

Packet client gửi khi đánh quái là opcode `6`:

```text
byte  skillIndex
byte  targetCount
short targetId[targetCount]
```

Packet đánh người chơi dùng opcode `9` và có cấu trúc tương tự. Không packet nào gửi damage, attack stat, HP mới hoặc MP mới.

Combat response từ server được đọc tại `er.a(ep, ..., targetType)`. Với mỗi target, server gửi:

```text
short targetId
int   damage
int   remainingHp
...   effect data
```

Sau danh sách target, server còn gửi HP và MP còn lại của người ra đòn. Client chỉ hiển thị `-damage`, gán `target.bs = remainingHp`, rồi gán HP/MP của attacker theo response.

Vì vậy damage skill lên quái/người chơi được tính hoặc ít nhất được quyết định ở server. Client không gửi con số damage lên.

## 3. Damage từ quái

Sự kiện quái/NPC dùng chiêu được nhận qua opcode `40` (`er.N`). Packet chứa loại attacker, ID attacker, skill/action index, giá trị effect/damage và ID/type target. Client dùng dữ liệu này để chạy animation và tạo effect.

HP thực tế được đồng bộ bằng packet server, nổi bật là opcode `32` (`er.E`):

- nhánh `0`: server gửi max HP, current HP và delta;
- nhánh `1`: server gửi max MP, current MP và delta;
- các nhánh khác cập nhật HP theo loại hiệu ứng.

Không thấy công thức client kiểu `monsterAttack - playerDefense` để tự quyết định HP sau khi bị quái đánh. Client nhận kết quả rồi gán lại HP/MP. Do đó damage quái là server-authoritative.

## 4. HP, MP và các chỉ số liên quan

Ánh xạ field chính trong `fa`/`bq`:

| Field | Ý nghĩa |
|---|---|
| `bs` | HP hiện tại |
| `bt` | HP tối đa |
| `bu` | MP hiện tại |
| `bv` | MP tối đa |

Khi đăng nhập/khởi tạo nhân vật, `er.b` đọc bốn giá trị này từ server. Các packet server sau đó tiếp tục gán lại giá trị tuyệt đối. Client dùng chúng để hiển thị và làm pre-check (đủ mana, HP/MP đã đầy, tự dùng potion).

Phần trừ MP ngay lúc animation ra đòn là prediction phía client. Response combat sau đó ghi đè bằng MP do server gửi. Không thấy client tự cộng regen HP/MP để làm giá trị chính thức trong các lớp combat đã rà.

## Bằng chứng mã nguồn dễ đọc

- Khởi tạo HP/MP từ server: `src-decompiled/er.java`, dòng 228-239.
- Server gửi metadata skill: `src-decompiled/er.java`, dòng 3049-3097.
- Server cập nhật HP/MP tuyệt đối: `src-decompiled/er.java`, dòng 3126-3210.
- Nhận đòn quái/action: `src-decompiled/er.java`, dòng 3619-3668.
- Nhận damage, remaining HP và HP/MP sau combat: `src-decompiled/er.java`, dòng 3752-3843.
- Client gửi skill/target, không gửi damage: `src-decompiled/q.java`, dòng 86-99 và 401-413.
- Client kiểm tra cooldown/mana: `src-decompiled/bq.java`, dòng 1147-1170.
- Client bắt đầu cooldown và trừ MP dự đoán: `src-decompiled/bq.java`, dòng 1230-1250.
- Client đếm timer cooldown: `src-decompiled/bq.java`, dòng 687-702.
- Thời điểm gửi request trong animation: `src-decompiled/dh.java`, dòng 168-213.

## Giới hạn kết luận

JAR là client nên chỉ chứng minh chắc chắn được dữ liệu client gửi và dữ liệu server trả về. Muốn xác nhận server có validate cooldown, range, tốc độ đánh, mana cost và target hợp lệ ở mức nào, cần source server hoặc capture request/response trên một server thử nghiệm được phép kiểm tra. Không nên suy ra “không thấy check trong client” thành “server không check”.
