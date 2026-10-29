# Project HSO

## Client release layout (2026-09-24)

`D:\project\hso\client\jar` is the release directory. Keep one verified,
playable JAR per server endpoint; variants for different servers may coexist.
Put no documentation, source, manifests, build output, baselines, or dependency
JARs in the release root. Before replacing a server-specific JAR, back it up
under `client/jar/backup/`; the canonical Luna source build keeps its prior
release under `client/workspace/inputs/releases/`. Do not replace a release
with an unverified build.

Client development files live in `client/workspace/`:

- The byte-for-byte source baseline is
  `client/workspace/inputs/luna_final-golden-baseline.jar`.
- Maintained Luna Java lives in `client/workspace/src/luna/`,
  `client/workspace/fixtures/luna/`, and `client/workspace/tools/luna/`.
- Legacy sources are grouped under
  `client/workspace/tools/legacy/{luna,hsosky,ghostmod,shared}/`.
  Do not add new probes at root; put maintained tools under `tools/` and keep
  temporary investigation outside the repo.
- Decompiled trees, scratch probes, and generated bridge/classes are
  disposable. Previous verified releases may be retained in
  `client/workspace/inputs/releases/`; never put them back in `client/jar/`
  alongside the current release.
- Root-level client `.txt` files are disposable bytecode/log dumps. Preserve
  `client/workspace/fixtures/luna/runtime-trace.txt` and the separate server
  data file `server/data/msg.txt`.
- ASM, microemulator, and other build dependencies are external. The source
  build accepts them through `-DependencyDir` or `LUNA_DEPS`; they are not
  stored in the repository.

The server source under `server/src/main/java/` is separate and was not
refactored by the client cleanup.

Hai phần độc lập của cùng dòng game Hiệp Sĩ: mod client J2ME, và mã nguồn server.
**Cùng game nhưng khác version** — đừng dùng tài liệu bên này làm căn cứ cho bên kia.

| Thư mục | Nội dung |
|---|---|
| `client/jar/` | Chỉ chứa một JAR phát hành đã kiểm tra. |
| `client/workspace/` | Mã nguồn, công cụ build, fixtures, symbols, tài liệu, baseline và build tạm. Xem `client/workspace/CLAUDE.md`; **chạy lệnh build từ thư mục này**. |
| `server/` | Server Java 17 + Maven + MySQL — game "Hiệp Sĩ Free Online" (một server khác client trên). Đã vá lỗi + thêm công cụ admin + file container (23/08/2026). Xem `server/CLAUDE.md` và `server/SERVER_REPORT.html`. **Lệnh build phải chạy từ trong `server/`**. |

Repo là git (khởi tạo 23/08/2026). `.gitignore` loại `target/`, `log/`, `*.class`, `*.mdmp` và
các thư mục decompile — những thứ đó sinh lại được.

## Tài liệu

| File | Nội dung |
|---|---|
| `server/SERVER_REPORT.html` | Khảo sát mã nguồn server: kiến trúc, 55 opcode, lược đồ DB, danh sách lỗi theo mức ưu tiên kèm `file:line`, runbook vận hành, lộ trình nâng cấp. Đọc trước khi sửa server. |
| `client/workspace/PROTOCOL.md` | Protocol mạng của **client** đã mod: frame, handshake, keystream, opcode, kỹ thuật mod đã verify. Của version server khác. |
| `client/workspace/HSOSKY_COMBAT_ANALYSIS.md` | Phân tích luồng combat phía client. |
| `client/workspace/AUTOTRADE_HAUL.html` | Chặng dắt bò của Auto Đi Buôn: luật server, tuyến 52→8, thứ tự kiểm tra một nhịp dắt, bảng hằng số, xử lý mất bò. Mở bằng trình duyệt; file local, không publish. |

# Luna AI refactor routing

Luna client and the game server are independent pipelines. Do not use server
version, protocol notes, or build output as client truth. For any Luna change,
read [client/workspace/CLAUDE.md](client/workspace/CLAUDE.md) first.

The stable client artifact is `client/jar/luna_final.jar`, version 4.0.2,
SHA-256 `6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`.
The source baseline and rollback target is
`client/workspace/inputs/luna_final-golden-baseline.jar`, SHA-256
`06622CD81DC6790871A5863D465D4E352B29A94BB6FD0062C54F7CF9C6C47E0F`.
If the versioned symbol
catalog already exists, do not decompile the JAR again; use
`client/workspace/symbols/` and `client/workspace/tools/luna/`.

| Kind | Location | Rule |
|---|---|---|
| Semantic source | `client/workspace/src/luna/` | Hand-written API, core, feature, and UI logic. |
| Generated artifact | `client/workspace/build/` | Recreate; do not edit by hand. |
| Stable client | `client/jar/luna_final.jar` | V7 menu-restored default release. |
| Source baseline | `client/workspace/inputs/luna_final-golden-baseline.jar` | Preserve byte-for-byte for rebuild/rollback. |
| Test fixture | `client/workspace/fixtures/luna/` | Deterministic host tests and traces; no deleted-JAR references. |
| Symbol catalog | `client/workspace/symbols/` and generated map | Versioned binary lookup only. |

The stable release is `client/jar/luna_final.jar`, copied from
`luna-4.0.2-login-reconnect-v7-menu-restored-default.jar`.
(SHA-256 `6B3783A0D94D5C73B994440B8DC44160860A0283DA076A4E86F366FDBE1DB0CE`).
It keeps the Luna GhostMod/captcha removal, horse first-open/login guards,
one-shot white-horse lifecycle retry, native horse spam gate, exact-error
modal suppression for the two Auto Horse failure messages, and a connection-wait
gate before the first mount request, and one-shot login retry after the server's
10-second reconnect cooldown, strict world-state validation, disconnect-triggered
retry arming, no per-frame loading reset, and a guarded 30-second loading-screen
watchdog. The v12 retry also prevents native `onCanvasUpdate()` from treating
the stale player object on the logo/loading screen as a completed world entry;
the disconnect flag therefore remains observable until the strict game-screen
and map/player check succeeds. The v15 retry keeps the original TCP session for
a server cooldown notice and only re-runs the original disconnect handler's
socket/thread rebuild for a real disconnect or a loading watchdog retry. The
v22 build fixes the deeper transport race: the server sends the cooldown as
incoming opcode 2, whose native internal presenter bypasses `CANVAS.showDialog`;
the patch now observes its `readUTF` text before the presenter consumes it, so
the delayed retry is armed by the actual network packet. It also observes the
real outbound opcode-1 login request and waits for the native reader to consume
handshake `-40` and enable encryption before sending opcode 1 on a rebuilt
transport; this avoids queueing login against a socket whose crypto handshake
has not completed. When the current transport is known alive, v22 replays the
exact captured opcode-1 serializer call instead of depending on the login UI
object. The
native auto menu remains available; the
one-shot ENTER hook still closes it after login/world entry. XP presentation
patches remain enabled, while the old native `Tự động` suppression is not used.
The XP type-1 popup now has a cumulative 2.25x baseline lifetime and a 4/9
baseline fall profile; other popup types are unchanged.
It also adds the Auto attack menu choices `Auto chỉ quái` and `Auto quái + PK`,
with mode 7 (monsters only) as the default. Selecting a choice keeps the old
chat 7/6 behavior and enables Auto attack.
The press is debounced per login/world transition and does not run every frame.
NPC menus, the Luna
mod menu, dialogs, and server notices are not dismissed. The source golden
`luna_final-golden-baseline.jar` remains the rollback target; the two generated XP-font resources remain
allow-listed in the client verifier.
Its `HSOAutoTradeSupport` class is only the stable hook façade; generated
binary compatibility code is isolated under `client/workspace/build/generated/` and
the semantic source is under `client/workspace/src/luna/`. Follow the complete
source-to-JAR cookbook in [client/workspace/CLAUDE.md](client/workspace/CLAUDE.md).

Older refactored output JARs are historical and were intentionally removed.
Recreate releases only through the canonical source build with external
dependencies; never restore a pile of historical JARs or decompiled sources.

Do not reset, clean, overwrite, or delete existing user changes. Build every
new JAR under a new filename, keep the input golden untouched. After the new
JAR passes the required checks and is published, update the client version
matrix and `Last verified` line in `client/workspace/CLAUDE.md`, then update
the active release references in this file. Record its actual filename, hash,
manifest, and verification status.

## Stable release

The active client is `client/jar/luna_final.jar`, copied from the verified
`luna-4.0.2-login-reconnect-v7-menu-restored-default.jar` release.
It keeps the native `Tự động` menu available, preserves the one-shot ENTER
cleanup after login/world entry, and only permits the custom reconnect watchdog
when an Auto Login character is selected. Manual login no longer triggers the
10-second custom reconnect. The abandoned V8–V49 reconnect experiments
were removed from the workspace; the source golden remains separately stored
as `client/workspace/inputs/luna_final-golden-baseline.jar`.

The separate HSO CHILD endpoint JAR is `client/jar/hsochild_final.jar`
(HSO CHILD / 4.0.2, endpoint `221.121.0.242`). Its inherited cast/auto-combat
request gate is 100ms, matching the HSOSKYMOD sample. Its movement cadence was
raised from 5/8 ticks (x1.25) to 8/8 ticks (x2); SHA-256 is
`D7FC5548F9D0ED9A09ACA443D218857616BAAE8EBD3AF30BAD185FE399D26767`.
The 250ms player-update checks are also present in the sample and are not the
Auto Tự Đánh cadence. Basic-hit rate, movement on the live server, and server
acceptance remain untested. Build record:
`client/workspace/build/hsochild-run-speed-2x-20260924.manifest`.
Previous variants are backed up under `client/jar/backup/`.
