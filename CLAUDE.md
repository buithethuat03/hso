# Project HSO

Hai nửa của cùng một game: client J2ME đã mod, và mã nguồn server.

| Thư mục | Nội dung |
|---|---|
| `client/jar/` | Toàn bộ việc mod client: patcher ASM, support class, jar gốc + jar đã mod, source decompile. Xem `client/jar/CLAUDE.md` để biết cách build — **lệnh build phải chạy từ trong thư mục đó**. |
| `server/` | Mã nguồn server (chưa có, sẽ giải nén vào sau). |

## Tài liệu tham chiếu

- `client/jar/PROTOCOL.md` — protocol mạng đã reverse từ client: frame format, handshake,
  keystream XOR, bảng opcode, các kỹ thuật mod đã verify. Khi có mã nguồn server thì đây là
  tài liệu để đối chiếu: bảng opcode dựng từ phía client, server sẽ xác nhận hoặc bổ sung.
- `client/jar/HSOSKY_COMBAT_ANALYSIS.md` — phân tích luồng combat phía client.
