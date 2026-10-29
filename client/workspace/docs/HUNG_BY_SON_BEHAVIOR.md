# Hung_By_Son client behavior

## Independent Auto đánh

The single-client MOD menu adds **Auto đánh >** with a master toggle, monsters-only
mode 7, monsters plus eligible PK mode 6, and a separate 1–1000ms real-time delay
(default 650ms). Choosing a mode enables the feature. Enabling it stops Farm;
enabling Farm stops it, preserving both configurations. Mode/delay persist in a
separate direct MIDP store; a fresh client always starts Auto đánh OFF. Lifecycle,
ownership, cadence and build details: [Auto đánh](HUNG_BY_SON_AUTO_ATTACK.md).

The configured delay now applies to both Farm and Auto đánh in the same client;
manual/remote requests retain 650ms. Manual movement takes priority over new
Auto casts: held directions or accepted ground/portal moves cancel pending work,
release the attack state and pause Auto without turning it OFF. After stopping,
Auto waits 100ms before its first resumed cast; later casts use the configured
delay. Native cooldowns and skill intervals still apply. The native inventory
screen keeps Auto running when its parent is the world; inventory navigation
does not request character movement. X3 has the same behavior in
each of its three independent slots, while the EXP table stays shared.

The current Hung x1/x3 monster cache excludes `Xà nữ`, `Lửa thiêng` and `con Ma`
by trimmed, case-insensitive template name. This applies while learning, loading,
restoring and purging old records, including when the name catalog arrives late;
only cache-owned placeholders are removed. Native server objects remain intact.

The Chọn quái menu also hides `Lửa thiêng` and `con Ma` at every level, using
trimmed, case-insensitive exact names across live, remembered and saved groups.
Old selected object groups are ignored without losing other monster choices;
an object-only saved profile falls back to all monsters. Xà nữ stays selectable.

Monster cache skips map ID 48 entirely: no loading, learning, restoration,
recovery or RMS writes, including forced flushes. Normal native monster data
remains usable there. Legacy map-48 cache contexts are removed when loading
elsewhere and omitted from the next permitted save; other maps retain their data.

## Local mod services

The mod-service boundary is fully local. Activation, all seven entitlement flags,
and startup usage instructions are supplied in the client; no activation or
Brain request worker is started. The formula getter handles all ten audited
enhancement/trade keys locally and has no HTTP fallback.

From the x1 build after `C9E5073D…` (2026-10-27) the item-menu **Đập đồ** button and
MOD **Đập đồ >** replace Auto Cường Hóa with ModUpLv's enhancement loop, which sends the
same opcode-67 steps wherever the character stands; see
[HUNG_BY_SON_ENHANCE.md](HUNG_BY_SON_ENHANCE.md). The rest of this paragraph describes the
old module, still the behaviour of X3.

Auto Cường Hóa uses the native equipment/clover selection and gold enhancement
packets. Before selecting equipment, it synchronizes position, requests the
nearby NPC's menu/data, selects the equipment-enhancement row by label through
the native menu dispatcher, and waits for the native enhancement screen to
confirm the session. It then requires a fresh equipment acknowledgement; an
old selected-item pointer is not treated as confirmation. Native enhancement
notice type 3 aborts with the game's text, and generic game notices received
while waiting are retained for the timeout message. The **Thông minh** rule uses a four-leaf clover only when the item's
current level is +8, +9 or +10; it uses no clover at other levels. Manual modes
still follow the selected mode. IDs are read from the live bag, and a missing
requested clover stops the module. A previously selected clover is removed when
the next attempt requires none. Requests are at least 500 ms apart; an
enhancement request must receive its native result before another request. If
the native enhancement tab is visible, its result animation runs normally. In
background auto, the static animation state is released 500 ms after the real
result arrives because there is no visible tab update to advance it. A missing
result or selection acknowledgement for 12 seconds stops auto with a specific
reason. Normal pacing/acknowledgement waits do not increment the old MOD-secret
failure counter. If the player leaves the enhancement position, the controller
returns to its existing navigation phase.

The existing trade implementation and entitlement-gated Farm/Khu 2, dungeon,
equipment sets, buff and PvP controllers remain in use. See the Vietnamese
[feature inventory and usage guide](HUNG_BY_SON_OFFLINE.md) for the full audit,
the reconstructed instructions, verification evidence and live-game limits.

## Auto Login and inventory dialogs

The native Auto Login fallback no longer clicks ordinary dialogs during a
connected gameplay session. Opening the bag changes the active screen to the
native tab screen; previously, with Auto Tự Đánh or Farm active, a confirmation
could receive `b(0,0)` after more than 40 scans. That generic dialog action can
open the login screen instead of invoking its item callback. Confirmation text
containing `10`, including an item at +10, also reached a reconnect branch.

The guard applies only to the generic active-dialog fallback after world entry,
outside login, loading and character selection. It reads the existing connection
lifecycle flag, clears the modal scan and unknown-notice counters, and preserves
the retry deadline and captured credentials. The explicit loss-notice branch
remains ahead of the guard because the connection flag can stay true briefly
after a remote disconnect. Native post-world popup handling and authentication
commands are unchanged; inventory dialogs retain their native callbacks.

The release-byte regression reproduces the old tick-41 login and checks that the
fixed ordinary/+10 confirmations remain open after 1,000 ticks. Connected and
disconnected loss notices, observable login/character-selection commands,
authentication-screen dialogs, popup handling and retry state also pass. The
isolated emulator startup passes; live Bạch Hổ gameplay remains untested.
Artifact and verification details are in [RELEASES.md](RELEASES.md).

## Server list

The Vietnamese server list includes **Bạch Hổ**, with endpoint
`hs8.teamobi.com:19129`, copied from `HungSon-update.jar`. The entry is appended
after Server Lậu, preserving all nine existing server indices and the native
special handling of Server Lậu at index 8. Its port and language metadata are
copied from the donor's Bạch Hổ row (19129 and 0). The client retains the donor's
hostname and resolves its IP when connecting.

Archive comparison, JVM class linking, and initialized server-list inspection
passed. Server selection and login have not been exercised in a running game.

## Game speed and movement

The FPS selector is replaced by Bạch Hổ's `Tốc độ Game: [xN]` row and
`CHỌN TỐC ĐỘ` submenu. The validated menu fix offers x1, x2, x3, x0.8 and x0.9 with a `[x]` marker on the
current choice. Selecting a row sets that value directly and returns to the
main mod menu. Startup defaults to x1; this setting is not persisted to RMS,
matching the reference.

Native menu item actions are signed bytes. The submenu uses action IDs
101–105; the previous -3101–-3105 IDs narrowed to -29–-33, which sent x1
to the activation-key flow and other rows to unrelated actions. The native
button/callback regression now covers the actual click path. Fractional
speeds use 8/10 and 9/10 scaling with a carried remainder, reset on speed
changes. They scale game time while the combat clock remains real time.
Publication status is recorded in [RELEASES.md](RELEASES.md).

The native loop accumulates `2 * elapsed * speed`, uses 40 ms steps, permits
at most three simulation updates per pass, and clears a remaining backlog of
at least 120 ms. It refreshes the native request clock after every update.
Painting follows the updates, with a sleep of `max(1, 16 - paintCost)` ms.
Initial coordinate displacement follows Bạch Hổ's five-of-eight tick mask,
and the shared movement tick advances after each simulation update. The old
FPS remainder and local-player movement throttle are removed.

## Noclip

The main MOD menu has a `Noclip: [OFF]` / `Noclip: [ON]` row after game speed.
Pressing it toggles the state and refreshes the menu. Noclip starts OFF each
time the client is launched; it is not saved to RMS. The selected state remains
in effect across map changes in the same running client.

While ON, the native tile query and pathfinder treat wall values 1/-1 as clear.
This permits native key movement and route search through map obstacles.
Collision arrays retain their original values, so turning OFF restores the
native wall checks immediately. Native boundary checks and other terrain types
remain in use. Server movement responses and correction handling are unchanged.

Native collision/path and actual menu-click tests pass, including OFF behavior
against the previous release, four key directions, route crossing, boundaries
and unchanged raw collision data. Isolated emulator startup passes; wall
crossing and server movement acceptance have not been tested in a live session.
Build and backup details are in [RELEASES.md](RELEASES.md).

## Full-map monster targets

Monster selection and target retention use the reference's 2,000,000 range.
The selector keeps its native nearest-target ordering, applies the reference
monster exclusions, and the multi-target mark loop uses zero distance like
Bạch Hổ. Cast start enters the existing preparation/send path directly,
without calculating a path toward a distant target. This applies to loaded
objects on the current map; the existing Hung PK controllers remain in use.

## Twelve skill cells

Each of the three hotbar pages stores 12 cells. Cells 1–5 retain their original keys; cells 6–12 use F, G, H, J, K, L, and M. Skill and item assignment menus include all 12 cells. The horizontal and alternate layouts draw 12 cells, and the touch width follows the new cell count.

The save loader expands 5-cell and 10-cell pages to 12 cells. Previously assigned entries keep their page and position; new cells start empty. The client uses 24-pixel hotbar art, so logical screens narrower than 296 pixels may show overlapping cell backgrounds.

## Attack animation and request cadence

The automatic gate uses strict `elapsed > 500 ms` after the 2026-10-05
100ms increase from the previous 400ms release.
The native request comparison is `elapsed > nativeInterval + 300 ms`.
Auto selects the next skill only after the actor has left the attack state
and the automatic gate is ready. Cast start invokes the native visual/cooldown
routine synchronously; the network attack request still uses the native
`cQ == m` animation-frame condition and the separate request interval gate.
Native animation setup/recovery is retained; there is no forced frame-1 send
or frame-2 recovery override from the earlier 300 ms experiment.

Animation and the auto timer run concurrently; their durations are not added
to produce a fixed packet-to-packet delay. Live gameplay cadence remains unmeasured.

Each skill still has its own cooldown supplied by the game server, and the server decides whether to accept a request and apply damage. This patch does not change those rules or the network packet format.

The Investigate native-timing restoration originally restored the
auto initializer and strict >600ms gate, and removed the +300ms request addition.
Its request comparison is strict `elapsed > nativeInterval`, using native skill
metadata. This restoration changes only those two sites in one native class;
all animation, cooldown, full-map/cache/selection and stale-target mechanisms
remain intact. The stable `Hung_By_Son.jar` retains its existing timing. See
[Investigate instructions](HUNG_BY_SON_INVESTIGATE.md) and [RELEASES.md](RELEASES.md)
for candidate/publication status and verification limits.

The published Investigate auto-gate +100ms release uses strict >700ms while retaining
the dynamic native request comparison without an added delay. Only the auto
initializer changes from the verified original-timing/long-session release.
Current publication status is recorded in [RELEASES.md](RELEASES.md).

The Updated real-time profile uses strict `elapsed >550ms` for auto selection
and strict `elapsed >max(550ms, nativeSkillInterval)` for local attack requests.
Both use `System.currentTimeMillis()`; the request comparison and its accepted
timestamp read this clock directly. No +300ms addition is applied. Game speed
still changes simulation and animation cadence, but cannot shorten these waits.

The native request callback runs only at its exact send frame. Previously, a
not-ready interval could skip that request while animation advanced past the
frame. Updated marks that local request as waiting and its entity update skips
only the following animation advance. The frame is retried until ready; no
request timestamp or downstream dispatch is updated during the wait. On the
ready callback the original send path runs and animation resumes. Remote actors,
NPC handling, skill cooldowns, packet formats, cache and RMS are preserved.
550ms is a minimum threshold, not an exact packet schedule; a longer native
interval, skill cooldown, animation or tick cadence can delay a request further.
The Investigate 700ms file is retained. See [RELEASES.md](RELEASES.md) for
publication and runtime verification limits.

## Equipment enhancement animation

The enhancement screen shows the initial shrinking swirl for a minimum of 500 ms instead of 3700 ms. If the server result is already available, the screen then enters its existing success or failure effect. If the result arrives later, the client waits for that result before choosing the matching effect. The result effects themselves and the enhancement request/response handling are unchanged.

The three Bạch Hổ mechanisms passed runtime bytecode differential checks,
including speed selection, slow-frame pacing, clock updates, 16 movement
ticks, near/far/dead/player/tie target scenes, exact cast boundaries, and
synchronous send. Archive/class checks, isolated emulator startup, and the
existing local enhancement/NPC/trade fixtures also passed. Live gameplay and
server acceptance remain untested. Details: [parity audit](HUNG_BY_SON_BACHHO_PARITY.md)
and [release record](RELEASES.md).

## Automatic ghost captcha

When the server delivers a ghost captcha image (message 9999), the client reads the five digits (each 1, 3, 5, 7 or 9) from the pixel widths of the top and bottom text rows. This is the same rule GhostMod used. After 1.5 s the client sends the matching answers through the native ghost-answer packet, 1.5 s apart. It sends indices 0–4 for digits 1/3/5/7/9, as the first five skill keys would for a focused ghost. There is no menu toggle.

While the captcha image remains set, the same answer is re-sent every 20 s, up to 10 times. A wrong answer or the ghost's death clears the image. If the client then receives a new captcha, the new digits are answered immediately. The worker stops after 15 s without a captcha. Repeated delivery of the captcha currently being answered does not trigger a second press.

OCR accuracy against real server images, and the server's acceptance of the answers, have not been observed in a running game.

## XP popup

The single and all three X3 HUDs have an `i` icon below A. It opens a native confirmation dialog
with EXP/hour after five minutes of positive-reward session measurement,
whole level EXP when the outside estimate is ready, and remaining-level ETA
when both inputs exist. Missing values and the outside HUD use
`Đang tính toán...`. Whole EXP values use dot grouping. The icon uses a separate
12px image from the user's SVG, matching A's dimensions and aligned below it.
Only pointer confirmation closes it; ENTER and Farm
updates cannot dismiss the owned info dialog. Menu/wait overlays block icon
capture. X3 keeps rate, modal ownership and icon input state separate per slot;
only the persisted level table is shared. [EXP info behavior](HUNG_BY_SON_EXP_INFO.md).

The single and X3 HUDs interpolate three fractional percentage digits from clean live
EXP samples when a level total is available. It retains the exact native font,
plus sign, decimal separator and layout; the XP bar and bottom K/M estimate
keep their prior calculations. Unknown phase/total temporarily keeps native
one-digit precision. Each X3 slot tracks its own live phase. Details and limitations:
[EXP estimate](HUNG_BY_SON_EXP_APPROX.md).

The single and X3 releases also show a persisted EXP estimate below the native
level/progress line. Unknown values show `Đang tính toán...`; learned values use
`(37.4M/100M)`, with K/M only. Each slot learns only its local character,
shares cached bounds by level across characters/servers and all X3 slots, and
refines them from separate live-session packet intervals. A common direct MIDP
RMS backend avoids the old StringBuilder path and native login deletion, imports
surviving V1 records before cleanup, and serializes dirty-level merges. Account
and monster data remain isolated. The native frame becomes a uniform
96px by 56px rectangle, and the mail/count icon moves down. Behavior,
cache/reset rules and limitations: [EXP estimate](HUNG_BY_SON_EXP_APPROX.md).

Experience text from packet 30 (`<integer>xp`, popup type 1) matches HSO Luna. It is drawn in a 2x bold green font, rises 3–6 px, drifts left and falls under a gravity that applies on 4 of every 9 ticks. It lasts 45–54 ticks. Only the generated compact sheet (`x1/mfont/tahoma_14b_green.png`, `mfont/tahoma_14b`, containing digits, `x`, `p`, `+`, `-` and space) is added. Damage, HP and other popup types keep their previous font and motion.


## Auto Đi Buôn V3 offline

The Auto Đi Buôn V3 menu and flow are unchanged. It no longer needs the licence server; the actions are produced locally:
- **Summon and dismiss:** uses the chosen animal's item from the consumable bag. The name must start with Bò, Lạc Đà or Lừa.
- **Buy:**
  - The first time on a map, it opens the nearest NPC and presses "Mua bán". It remembers the shop type and the gem ids, then closes the shop.
  - After that it sends one buy request per gem, at least 2 s apart.
- **Revive:** revives in place with a ticket, otherwise returns to town.
- **Sell:** uses V3's own "Bán đá quý" menu click.

If the animal item is missing, a notice says so and V3 keeps retrying.

## Auto Thú cưỡi

Menu mod → Cài đặt cấu hình → Auto Thú cưỡi: chọn ngựa (Ngựa trắng/xích thố/đen…) và BẬT/TẮT. Chỉ chạy khi
Auto Farm BẬT và nhân vật còn sống. Mỗi 900 tick game (~18s ở x1) tìm món đầu tiên trong túi có tên chứa tên
ngựa đã chọn và dùng nó (op 32), **chỉ khi nhân vật chưa cưỡi** (`ef == -1`, cùng quy tắc menu Thú cưỡi gốc).
Sau khi dùng, chờ server cập nhật trạng thái cưỡi; hai lần không được xác nhận thì tự TẮT và báo. Mỗi lần
xuống ngựa hoặc chết, ngựa mất theo luật game nên lần lên lại tiêu một ngựa mới. Chi tiết và kiểm thử:
[RELEASES.md](RELEASES.md).

## Auto phục hồi thể lực

Menu mod → Cài đặt cấu hình → Auto Phục Hồi Thể Lực: mỗi giây xin thể lực (opcode 59) mà không mở
giao diện; dưới 500 thì ăn Cơm gà/Cơm đùi gà và tự đóng thông báo của server; ăn xong vẫn dưới 500
thì tự TẮT. Chi tiết: [HUNG_BY_SON_STAMINA.md](HUNG_BY_SON_STAMINA.md).


## Auto thay đồ khi BUFF

Menu mod → Cài đặt cấu hình → Auto Tự Đánh → Auto thay đồ khi BUFF: chọn chiêu buff đã học. Chỉ khi
Auto Farm hoặc Tấn công đặc biệt BẬT (và Thay Đồ BẬT): chiêu còn ≤ 2s hồi thì Mặc Set 1, server trả
lời buff (opcode 40) cho chiêu đó thì Mặc Set 2; chiêu vẫn do Farm/đặc biệt tung. Chi tiết:
[HUNG_BY_SON_BUFF_SWAP.md](HUNG_BY_SON_BUFF_SWAP.md).
