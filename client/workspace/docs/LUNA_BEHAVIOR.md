# Luna: feature map và ràng buộc hành vi

Đọc trước khi sửa tính năng tương ứng; đường dẫn dưới đây tính từ client/workspace/.
Trạng thái artifact và giới hạn xác minh: [RELEASES.md](RELEASES.md).

### Feature map

| Feature | Semantic owner | Legacy boundary |
|---|---|---|
| Auto Đi Buôn | `AutoTradeController` | `HSOAutoTradeSupport` hook façade |
| Auto Ngã 4 | `N4Controller` | movement/combat hook façade |
| Hung x1 B dungeon stand positions | `HungDungeonPositionController` | ModUpLv live-notice ingress + fixed movement via MovementArbiter; see `docs/HUNG_BY_SON_MODUPLV_PORT.md` |
| Revive | `ReviveController` | revive hook/bridge |
| Auto horse persistence | `PersistentHorseController` | `HSOSKYPetFeedSupport` RMS/mount bridge |
| Menu/HUD/notice | `src/luna/ui` | menu and paint hooks |
| Hung x1 pet/sale equipment filters | `EquipmentFilterPolicy`, `EquipmentFilterSettings` | generated `HungEquipmentFilterBridge`; four independent stat groups, sale quality AND optional locked-only AND group; no eligible item at near-full bag disables Auto Sell before travel. See [HUNG_EQUIPMENT_FILTERS.md](HUNG_EQUIPMENT_FILTERS.md). |
| Hung x1 sale return scroll | `SaleReturnScrollController` | generated `HungSaleReturnScrollBridge`; one bag scroll before travel, wait for server village map, 10s fallback to native route, preserve sale origin/Farm restore. See [HUNG_EQUIPMENT_FILTERS.md](HUNG_EQUIPMENT_FILTERS.md). |
| XP floating text | native popup type 1, verified by packet 30 symbols; current profile is 2.25x baseline lifetime and 4/9 baseline fall profile | `tools/luna/XpPopupPatcher`, generated 2x font resources |
| Login/native “Tự động” popup | native menu preserved + one-shot fallback | generated `postLoginEnterTick()` -> `pressOk()` after 500ms |
| HSO CHILD login authentication | opcode-1 serializer boundary | fields UTF 4–6 are forced to the current client key for native, automatic, and replayed login; legacy `|HSOR_SECRET_2026` suffix is suppressed |
| Hung By Son → HSO CHILD variant | singleton native server/login boundary | `hsochild_update.jar` exposes only `Server lậu` at index 0, binds list/direct endpoint to HSO CHILD, and supplies UTF 4–6 login keys for that selected server |
| Hung By Son → HSO CHIBI variant | singleton native server/login and RMS boundaries | `chibi_up.jar` exposes only HSO CHIBI; uses the current `Hso.jar` session signature, version and final `LOCK` UTF; all native/MOD data uses a fresh `HSO_CHIBI_` namespace |
| CHIBI Final + Luna Final (1) current-Hso compatibility | native login serializer, self-character initialization and inventory/storage parser | `chibi_final.jar` and `luna_final (1).jar` use the donor session-key signature; self bag capacity, full-list counts and storage capacity use signed shorts. Self bag pages use ceiling division with minimum one. |
| CHIBI Final X3 | pinned single-client transform plus existing virtual launcher | `chibi_final_x3.jar` copies every fixed source class to three namespaces; suite `HSO CHIBI FINAL X3`, slot RMS a/b/c, held-`*` menu switching. See `HSO_CHILD_X3.md`. |
| CHIBI Final → HSO MAX variant | native server tables, shared login serializer, socket and RMS boundaries | `max_final.jar` uses `HsoMax` / `sv.hsomax.com:19129`, donor version/key/device-tail contract and fresh `HSO_MAX_` stores |
| Auto attack target mode | `HSOSKYPetFeedSupport` mode state; default `autoPlayersEnabled=false` | `ENGINE.update` menu rows -> generated bridge actions; `Auto chỉ quái` = chat 7, `Auto quái + PK` = chat 6 |
| HSO CHILD movement cadence | native `ENTITY` movement update | `tools/luna/patch-run-speed.py` changes the inherited 5/8-tick mask to 8/8 ticks (x1.25 to x2) |
| HSO CHILD enhancement/celestial crafting result | shared native enhancement screen | result animation is disabled; no automatic celestial-crafting retry or NPC/menu replay is active |
| Bạch Hổ cast cadence | native cast controller and entity request gate | published release: auto gate 400ms; native skill request interval +300ms; separate per-skill cooldown and animation preserved |
| Bạch Hổ mount state | native/server mount updates | Auto Attack reengagement preserves the mount byte and its native movement bonus |
| Bạch Hổ auto diệt ma | shared GhostMod OCR port | captcha-image receipt starts `HungBySonGhostSupport`; no menu toggle |

### Known invariants

The 2026-10-28 CHIBI Final/Luna Final (1) compatibility profile is scoped to
the two explicitly named JARs; `luna_final.jar` without the copy suffix retains
its prior bytes. Opcode 1 sends raw credentials, version `9.9.9`, extensions
`0`, `0`, `0|<session-derived signature>`, the native selector/device fields,
device-info UTF and `LOCK`. All native/manual/reconnect call sites share this
serializer. The signature and bounded key wait are copied from the exact Hso
reference recorded in RELEASES.md, with no stored credential transformation.
Opcode 13's byte-zero/character-ID payload remains unchanged.

Opcode 3's self-character packet also carries a short bag-capacity field before
the optional pet, tail UTF and timestamp. Reading one byte shifts that mandatory
tail and can fail before the world-readiness flag is written. The capacity field
remains a native short; pages are `max(1,(capacity+41)/42)`, following Hso.
`EntrySelfProtocolBinaryTest` executes the real capacity-through-timestamp
bytecode slice with both absent and present pets; game state outside that slice
is stubbed. It reproduces the old failure and verifies the corrected readers and
page arithmetic, without claiming a live-server login or complete world replay.

The shared opcode-16/65 parser consumes two bytes for subtype-3/4/5/7 full-list
counts and for the opcode-65 capacity prefix, following that Hso reference.
Item processing, native state updates and other packet fields retain their
input implementations. Binary fixtures exercise the real reader branches and
reproduce the original one-byte cursor discrepancy; they do not establish live
server acceptance. See [the original comparison](CHIBI_LOGIN_AUDIT.md) and the
2026-10-28 release record for exact preimages, backups and verification scope.

`max_final.jar` uses the pinned `chibi_final.jar` as its feature baseline and
`HsoMaxMod.jar` as its endpoint/login reference. Both server tables remain one
`HsoMax` row, every selected-index write becomes zero, and the final socket
constructor binds the donor host and TCP port 19129. The opcode-1 serializer
sends version `2.5.8` and three donor key strings without the source's appended
secret suffix or final `LOCK` UTF. Its donor-specific byte 80 and short 2389
remain login payload values. Native username/password, selector and remaining
device fields retain their positions. MIDlet identity is `HSO MAX`; all native
and mod RMS operations pass through the `HSO_MAX_` namespace with scoped
listing/deletion. Receive handlers for opcodes 3, 5 and 15 consume one signed
byte per appearance-array element, following the original MAX wire format;
values are widened into the existing native short arrays. Reading two bytes
per element shifts the subsequent body/equipment IDs and must not recur.
Native loop and complete self-tail fixtures reproduce the old failure and
preserve all ten part IDs after correction. Full donor login packet,
socket-constructor and native account codec fixtures pass; visual recovery
and gameplay on the live server have not yet been checked.

`chibi_up.jar` is derived from the current Hung By Son single client. Both
native server tables contain only `HSO CHIBI` at index 0, with list/direct
endpoint `103.77.173.232:19129`. Every table assignment uses the singleton
factory, every selected-index assignment normalizes to 0, and cached/HTTP
server-list refresh cannot add rows.

All four native login call sites converge on one opcode-1 serializer. It
sends raw username/password, version `9.9.9`, two `0` extension strings and
`0|<16-hex signature>` as the sixth UTF. The signature algorithm and bounded
handshake-key wait come from the current `Hso.jar`; the native selector and
device fields remain in order, followed by the device-info UTF and `LOCK`
UTF. The authentication helper uses the current connection key on each send.

MIDlet name/vendor are both `HSO CHIBI`. The native RMS wrapper uses
`HSO_CHIBI_`; Auto Attack settings and the shared EXP backend also use this
wrapper. EXP migration is disabled so valid levels from earlier endpoints
cannot seed the new table. Existing native/MOD records are neither read nor
deleted. Full packet parity, storage isolation/restart and archive/endpoint
fixtures live under `fixtures/hung-by-son/Chibi*Test.java`. These fixtures
do not establish that a live server accepts login.

The CHIBI profile disables every monster-cache entry point and its static
cache-buffer initialization. It does not load/save learned monsters, restore
placeholders, poll their details, retain corpses or draw QCache. `hold` and
`advanceCorpse` return false, so the original native death, world removal,
animation cleanup and timed/server respawn paths run normally. The selection
menu uses the currently loaded native monsters without learned cache rows;
its saved selection and unrelated settings/EXP remain available. Cache-driven
stalled-target rechecks also stop. The Hung single/X3 releases keep their
existing cache profiles. `ChibiDisableMonsterCachePatcher` applies this
endpoint-only profile after the CHIBI endpoint/login/storage stage.

The Hung By Son-derived `hsochild_update.jar` uses the HSO CHILD endpoint
`221.121.0.242:19129` through the sole `Server lậu` row at index 0. Its special connection branch
sets the native `4.0.2` packet version because the original direct-endpoint
version field is empty. All four native login call sites share one serializer;
that boundary supplies `TUOITHO_SECRET_KEY_2026` in UTF fields 4–6 only when
the selected server is index 0. All server-table and selector assignment
paths retain that single row. Cached/remote list parsing and list-only HTTP
fetches are disabled. Old selected-index RMS records are interpreted as 0
without changing saved account bytes. Binary packet and endpoint probes,
class linkage, and emulator startup pass; live server login is unverified.

Its MIDlet name/vendor are `HSOCHILD`; native and mod RMS operations use
the separate `HSOCHILD_` prefix. Cache and monster-selection records use
`HSOCHILD_mc_v1` and `HSOCHILD_ms_v1`. Existing `inv_` Investigate records
are neither imported nor deleted. Shared-memory RMS tests cover account,
cache and selection isolation, scoped listing/deletion and persistence.

The current CHILD Update single/X3 variants use a 300ms real-time auto gate
and `strict >max(300,nativeSkillInterval)` request minimum, retaining each
skill's native cooldown and the pending send frame. Game-speed choices are
x1/x2/x3/x5/x10. The loop permits at most ten 40ms updates per pass and resets
backlog at 400ms, so x5/x10 work at normal frame cadence with bounded catch-up.
Actual-loop, native menu-click, timing and X3 namespace/RMS tests cover this
profile; gameplay/server acceptance remains unverified.

Auto Horse persistence keeps the native menu/RMS checkbox, but native
`onCanvasUpdate()` is gated so it cannot re-use the mount item every 2.5s.
Lifecycle code waits for a settled player/map/inventory, sends one request per
world, retries only the explicit transient item-use error twice, and treats
the already-mounted notice as terminal success.

The player has one path writer. Map transitions and dialogs have settle
windows. A missing pet/entity observation is not a loss until the settle window
expires. Symbol lookup always includes owner, member, and descriptor. The main
mod menu has exactly one `Auto Đi Buôn` row: the patcher reuses the baseline
row and routes it to the semantic action instead of inserting a duplicate.
The login path preserves the native menu whose title is exactly `Tự động` at
`MENUBOX.show`. The generated bridge also sends one index-5/ENTER press 500ms
after the login screen hands control to the world. It must be armed once per
login/world transition and never run continuously in the frame loop. NPC menus,
the Luna mod menu, dialogs, and server notices remain untouched.
HSO CHILD applies the current login key at the shared opcode-1 serializer rather
than at individual obfuscated call sites. This keeps manual login, native auto
login, and captured-request replay consistent. The three extension strings are
written as the key exactly; the older sixth-field suffix must not be appended.
XP text is the packet-30 popup type 1 only; damage, HP, shop, and dialog text
must not use the XP font patch. Its generated `tahoma_14b` image/metrics are
the only newly allowed resources. The generated image is a compact indexed
sheet containing only the packet-30 characters (`<integer>xp`) so it does not
starve the J2ME render/network loop. The cross-class generated font field must
remain public for CLDC access. The current XP profile applies an additional 1.5x
slowdown over the previous release: total lifetime is 2.25x baseline and the
launch/gravity profile is approximately 4/9 of baseline. Only type-1 XP popup
objects use this profile.
The Auto attack mode menu must keep exactly the labels `Auto chỉ quái` and
`Auto quái + PK`; the initial mode is monsters-only (mode 7), and selecting a
mode enables Auto attack with the same state transition as chat 7/6. The
original `Auto Tự Đánh [ON/OFF]` toggle remains available for disabling it.
HSO CHILD's cast/auto-combat request gate is already 100ms, matching the
HSOSKYMOD sample. Its separate 250ms player-update checks also remain in the
sample and are not evidence of the basic Auto Tự Đánh cadence. Treat basic-hit
speed as unverified until accepted combat requests are observed on the server.
HSO CHILD movement uses every update tick after the speed patch; the prior
five-of-eight mask gave x1.25 and the new eight-of-eight mask gives x2 under
the inherited game-loop speed setting. Only one byte in the `ENTITY` class
changed. Movement and position acceptance on the live server remain untested.
HSO CHILD auto-enhance uses one 500ms timer from each request. A response only
updates inventory state; it cannot trigger the next request early. If there is
no response, the same timer still advances the loop. The client hook does not
correlate a reply with a specific request, so a late response may be processed
during a later attempt. Live-server auto-enhance behavior remains untested.
The same native screen owns equipment enhancement and celestial-equipment
crafting animations. That result animation is disabled for both manual and auto
flows: the server-result hook clears the animation stage, spinner counter, and
input-lock flag immediately, and the screen update clears them again before the
native state machine can draw a frame. Server result readiness and inventory
packets remain intact, but the old animated result sequence and its delayed popup
are skipped. The prior close cleanup remains as a fallback. This path has passed
bytecode verification and emulator startup, but has not been exercised against a
live enhancement/crafting response.
For celestial crafting, network UI type `19` maps to native screen mode `3`.
The opcode `-105` result clears the native material vector and the server-side
recipe session cannot be restored reliably from the client-only experiments.
Those experiments were rolled back after live-server failure. The current release
does not record or replay the NPC-open request or opcode -30 menu chain and does
not suppress any crafting menus. A player must leave the screen and repeat the
native Hammer flow before crafting another celestial item.

### Bạch Hổ cast delay (2026-10-02)

The published `client/jar/bachho_final.jar` adds 300ms to both native cast pacing
gates, following three requests to add 100ms. The auto gate changes from 100ms to
400ms (300ms to 400ms since the preceding variant). The request gate compares
elapsed time with the native skill interval plus 300ms; the native interval field still receives the
original skill metadata. The comparison keeps its original strict `>` boundary.
This preserves separate per-skill cooldown timers, animation, mana checks and
packet senders. Actual cadence also depends on those unchanged gates and frame
timing. `luna_final.jar` and the other endpoint releases are unchanged.

The boundary fixture at `fixtures/binary/LunaCombatCadenceHostTest.java` executes
the real auto method with a deterministic clock and extracts the request
comparison from both JARs. It covers 0/100/600/32767ms native intervals and
threshold boundaries. Bytecode scope verification proves that all other
instructions and members are preserved. See [RELEASES.md](RELEASES.md) for the
artifact path/hash, rollback path, publication status and live-server validation limits.

### Bạch Hổ phantom white horse (2026-10-03)

Auto Attack recovery previously assigned mount value `1` to the local player in
`HSOSKYPetFeedSupport.reengageAutoAttack()`, without a mount response from the
server or an enabled Auto Horse setting. This method is also called when
recovering from movement, selecting an attack mode, and restoring combat after
AutoTrade/N4. The same native mount byte selects both the horse presentation
and the movement speed bonus, so the local assignment could create a white
horse and a speed mismatch while the character was actually unmounted.

`tools/luna/NativeHorseStatePatcher.java` discovers the native mount field from
its exact speed accessor and removes only that three-instruction assignment
from the current Bạch Hổ binary. It preserves the other combat recovery state,
native mount packet handlers, manual riding, Auto Horse lifecycle/throttle,
and the previously increased combat delay. The legacy PetFeed source is not
rebuilt because it does not represent the support class in this release.

The binary fixture `fixtures/binary/LunaHorseStateHostTest.java` executes the
before/after recovery methods in an isolated harness. Unmounted state `-1`
and existing mounted states must survive repeated recovery calls; missing
player and non-positive HP guards must keep their native behavior. The fixture
also checks that native mount writers remain intact. Host and emulator startup
checks do not establish live-server movement acceptance; see the release record
for the remaining validation limit.

### Bạch Hổ auto diệt ma (2026-10-03)

The Bạch Hổ variant automatically reads the five-digit ghost image using the
GhostMod OCR port already maintained in
`tools/legacy/hsosky/HungBySonGhostSupport.java`. It starts when the native
network handler stores the special captcha image, waits 1.5 seconds, and sends
the native answer indices for digits 1/3/5/7/9 at 1.5-second intervals. There is
no menu entry or extra activation step. This variant-specific request does not
enable GhostMod in the Luna release.

The port retries 20 seconds after a completed sequence while the native
captcha holder remains present, with at most ten sequences per captcha. A
worker exits after 15 seconds without a pending captcha. Repeated delivery of
the current digits is ignored; a new delivery after an observed clear can
reuse the same digits. The network hook and native answer bridge are resolved
structurally by `HungBySonGhostPatcher`; no legacy combat, login, revive, horse,
or pet-feed source is rebuilt.

`fixtures/binary/GhostCaptchaHostTest.java` exercises the candidate's OCR and
controller with synthetic opaque/transparent images, simulated native bridges,
and deterministic time. Its checks cover answer mapping, key spacing, retry,
duplicate delivery, clear/new delivery, idle exit, and attempt limits. Native
linking and emulator startup are separate checks; these do not establish OCR
accuracy or answer acceptance on a live Bạch Hổ server.

### Bạch Hổ login defaults and saved-position combat (2026-10-03)

**Thử nghiệm đã rollback ngày 2026-10-04 vì người dùng báo bản không đạt.**
Release hiện tại là bản trước ba nâng cấp: Noclip/default combat theo bản cũ,
không có menu lưu/xóa tọa độ mới hoặc hook tự đóng nguyên liệu trắng mới.
Phần dưới lưu lại thiết kế thử nghiệm để điều tra, không mô tả release hiện hành.

Noclip starts OFF in Auto Đi Buôn and the runtime collision flags. A detected
native login/disconnect boundary resets the previous session once; ordinary
map/zone transitions preserve the current choice. N4 keeps its temporary
Noclip requests and restores the underlying session preference.

The new **MENU (mod) → Auto tự đánh >** menu provides **Tự đánh [ON/OFF]**,
**Lưu tọa độ** and **Xóa tọa độ đã lưu**. Saving records the current map, raw
zone and pixel x/y in a separate RMS store. The label shows khu +1 and tile
coordinates (pixels /24). Saving again replaces the spot. Clearing persists
the absent-position marker and resumes ordinary native auto combat if it is
enabled; enabling with no position never implicitly captures a new spot.
The existing native toggle, monsters-only/PK modes and auto intent RMS remain
available and synchronized.

With a saved position, enabled combat returns to its map through the existing
world graph/path planner, requests the saved zone at least 2.5s apart and waits
10 updates after map/zone changes. Outside a 30px Manhattan leash, it pauses
combat and requests a return path only when the existing path is idle. Within
30px it anchors x/y and step destinations to the saved spot, clears displacement,
sets the native combat origin and reengages. Native auto paths are blocked for
this stationary mode; native target validation and attack cadence remain in
use. No 280px or 600px target gate is added: in the donor those values belong
to its separate moving-attack mode and loot scan. AutoTrade/N4 take priority.
Legacy recovery yields while saved-position combat owns the world, and native
reengagement yields only during the return so it cannot overwrite the path or
origin each frame. Current Bạch Hổ zone storage is zero-based and its native
zone request uses opcode 51, verified from the current binary.

The once-per-login material controller waits 5s for a ready world, opens the
material submenu with `(125,125,0)`, then waits 2.5s and reads the observed
`(-125,-125)` server menu's first row. “Mở…” means the white material drop
setting is already OFF. Otherwise it selects `(-125,-125,0)` to close it.
An unread label permits one retry and never a blind toggle. It hides only the
observed material menu, watches relevant material confirmation text for 3s,
presses OK once and releases its key next tick. Unrelated dialogs remain.
No auto-buff worker is added. The donor binary's literal material helper has
no verified login caller; the active Bạch Hổ lifecycle hook is new.

Host fixtures, actual candidate RMS codec/hook checks, initialized legacy
classes and isolated emulator startup pass. These checks do not establish
server acceptance, rendered gameplay or real login/reconnect behavior. Artifact,
rollback and evidence are recorded in [RELEASES.md](RELEASES.md).
