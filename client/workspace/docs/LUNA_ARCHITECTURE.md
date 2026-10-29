# Luna semantic architecture

This document is the design reference behind `client/workspace/CLAUDE.md`. The
binary `inputs/luna_final-golden-baseline.jar` remains the golden rollback artifact. Semantic source
is the place to add behavior; version-specific binary access is isolated in
the generated bridge and hook façade.

## Module ownership

| Module | Ownership |
|---|---|
| `src/luna/api` | Stable bridge contract, reusable DTOs, actions, and clock interface. |
| `src/luna/core` | Runtime lifecycle, movement ownership, route strategies, portal graph, and timing. |
| `src/luna/features` | AutoTrade, N4, revive, and ghost/captcha state machines. |
| `src/luna/features/AutoCombatController` | Optional Bạch Hổ stationary combat around a persistent map/zone/pixel spot; native combat passes through when the spot is absent. |
| `src/luna/features/AutoAttackController` | Hung's independent mode 7/6 attack intent, target lifecycle and configurable real-time cadence. Version-specific combat and UI access use `AutoAttackAccess`. |
| `src/luna/features/AutoPkTargetPolicy` | Hung x1's reusable top-20 eligible-player ranking within 400 world pixels, with optional level-40 battlefield priority. The optional `AutoPkPriorityAccess` profile keeps the native-cast PK path consistent; the existing special engine keeps its cadence and skill policy. See [Auto PK](HUNG_BY_SON_AUTO_PK.md). |
| `src/luna/features/HungDungeonPositionController` | Hung x1 map-48 gate announcement parsing and fixed stand tiles; the ModUpLv boundary handles live notices, movement ownership and the scoped gold-chest pickup exception. |
| `src/luna/features/HungMazeCombatController` | Identity-scoped native door-guard cast ownership for exclusive Hung x1 Maze; the boundary releases it on world/actor changes and keeps Farm OFF cleanup from canceling a live Maze cast. |
| `src/luna/features/BattlefieldSupport` | Session-only opt-in battlefield policy and unique FIFO of ten server-reported final-hit player names; native access uses `BattlefieldAccess`. |
| `src/luna/features/LoginMaterialController` | Bounded per-login material-setting transaction with observed server-menu state. |
| `src/luna/features/ExpRateSupport` | Per-character EXP/hour: five-minute warmup, elapsed window growing to fifteen minutes, then rolling 900 one-second buckets; remaining-level ETA independent of estimator segment resets. The generated EXP info bridge owns native HUD touch and modal rendering. |
| `src/luna/features/GoldRateSupport` | Session wallet baseline, positive observed receipts and gold/hour using the same 5–15 minute window. Native wallet writes feed the generated bridge; the fourth information tab refreshes once per second. |
| `tools/luna/HungInventoryInfo.java.in` | Native bag occupancy/capacity in bold yellow; OFF/ON/COMPACT modes with a dedicated per-slot RMS setting. Draws before the old detail gate so COMPACT keeps the inventory row visible. |
| `src/luna/ui` | Semantic HUD/notice state. Rendering and menu hooks stay at the boundary. |
| `src/luna/features/EquipmentFilterPolicy`, `EquipmentFilterSettings` | Pure equipment stat groups and independent MIDP RMS settings for pet/sale filters. A generated bridge adds native menu/predicate/preflight hooks while preserving original quality, candy and sale cleanup behavior; see `docs/HUNG_EQUIPMENT_FILTERS.md`. |
| `src/luna/features/SaleReturnScrollController` | One-shot wait/arrival/fallback state for Auto Sell return scrolls. A generated bridge identifies the native bag item, invokes the unchanged item-use service and controls the existing route flags; source map and Farm restore remain native. |
| `tools/luna` | Symbol/catalog checks, generated-bridge inputs, JAR verifier, and build-manifest tooling. |
| `symbols` | Versioned baseline metadata plus the generated obfuscated symbol map. |
| `fixtures/luna` | Host-side deterministic tests and runtime traces. |
| `docs` | Architecture and migration notes. |
| `build` | Generated output only; never hand-edit generated classes or a JAR. |

## Runtime boundaries

`LunaRuntime` owns the common frame lifecycle. It ticks revive and ghost
guards, then combat and trade controllers. Every controller reads through
`LunaBridgeApi`; no feature reaches into a binary class directly.

`MovementArbiter` is the only owner of a player path. A feature must acquire
its owner, submit a path, and release it. The priority order is revive,
combat, N4, then AutoTrade. A higher-priority owner can preempt a lower one;
an old owner cannot write a new path after preemption.

`PathPlanner` exposes named strategies: straight route, native engine route,
walkable-tile BFS, and segment sampling. `WorldRoutePlanner` owns the versioned
portal graph and deterministic next-hop lookup. These helpers are pure and
host-testable; native engine reads/writes remain bridge responsibilities.

## Stable API

`LunaBridgeApi` reads world/player/entity/menu/shop snapshots into the mutable
`LunaPlayerView`, `LunaEntityView`, `LunaMenuView`, and `LunaShopView` objects.
It sends a reusable `LunaAction` and reports captcha visibility. DTOs are
intentionally mutable and reusable so a frame does not allocate a new object
for every entity or notice.

`LunaClock` is the only time dependency. Production uses
`SystemLunaClock`; host tests use `FakeLunaClock` to advance time explicitly.

Hung's independent Auto đánh is registered through its existing module lifecycle,
outside the Luna trade runtime. `AutoAttackRuntime` is its hook ABI and owns one
controller per running client. A controller monitor serializes menu transitions,
legacy Farm changes, local auto selection and tagged requests through enqueue.
Cast generations distinguish new, cancelled, manual and remote requests. Its
direct MIDP settings store holds mode/delay, with enabled always OFF at startup.
Farm and Auto đánh read one configured real-time delay per client; their timers,
targets and selection policies stay separate. Native Farm cast provenance leaves
manual/remote requests at 650ms. Manual movement cancels a new Auto cast before
native busy-state movement guards and pauses intent through the walking path.
The first successful cast after movement uses a fixed 100ms resume wait;
subsequent casts use the configured cadence. The native cast initializer
captures that one-cast exception, and native skill intervals remain in force.
Hung x1 additionally offers a session-only special-attack toggle. It preserves
native hotbar, target selection and fanout, but dispatches synchronously at the
prepared cast boundary before animation starts. Cooldown/MP bookkeeping runs
once and the cast tag is cancelled to reject old callbacks. This mode bypasses
the configured delay and native interval; a controller clock enforces only the
donor's strict 600ms gate for skill IDs 7/8/19/20 since any special-mode send.
The same toggle now covers Farm casts with the exact local initiating-thread
provenance marker. A shared semantic selector ranks the currently configured
hotbar skills by descending ID using native eligibility before the 600ms gate;
a ready gated winner reserves the next send instead of falling back and
refreshing the clock. Native Farm cursor writes preserve the actual sent slot,
or the old cursor if the winner is waiting. Manual/remote requests retain their
prior behavior. The default remains normal.
With Farm and Special enabled, read-only render accessors replace the local
idle/attack pose with a stationary sprite and hide auxiliary cast layers.
Only graphics methods and their pure atlas-index helpers use those reads;
native simulation fields are never reset. Player update, cast initialization,
fallback and animation timing retain the exact working Farm-special baseline
bytes, so callbacks, buffs, HP/MP, revival and automatic return movement continue.
Walking, remote actors and death retain native rendering. Farm's existing manual
movement restriction remains native. This replaces the withdrawn global idle
hooks that cleared paths and interrupted action state every player update.
At the saved spot (Farm state 0, same map/zone, inside its stand/move radius),
Farm + Special hands the tick to `AutoAttackController.farmSpot` through
`FarmSpotAccess`: two guards in Farm's tick skip only the legacy-auto rearm and
the native hotbar cast loop. The controller holds the legacy walking auto, keeps
a nearest Farm-selection target and sends through the same Special chooser;
sends reset Farm's 400-tick no-attack counter. Death, map/zone/distance return
states, revival, captcha and village travel stay native.
The exact native inventory screen with world parent stays ready and uses its
existing world update; inventory navigation keys do not request player movement.
Monster cache learning, loading, restoration and RMS writes are disabled for
map 48. Legacy records for that map are filtered while other contexts remain.
The CHIBI endpoint additionally disables the complete cache ABI and cache
initialization after the endpoint/login/storage build. No cache bridge is
called by those entry points; native death/removal/respawn guards fall through.
This profile is confined to `chibi_up.jar` and its three copies in
`chibi_up_x3.jar`; shared cache source and the Hung
single/X3 artifacts retain their current behavior.
The generated adapter alone uses native symbols; the controller never invokes
Farm's selection, skill policy, paths or update loop. See
[Auto đánh behavior and build](HUNG_BY_SON_AUTO_ATTACK.md).

X3 duplicates the complete verified client into three namespaces with isolated
controllers and RMS aliases. Hung-derived profiles keep the EXP level backend
as one unchanged root class outside slot static reset; all other game references
stay within the slot. Chibi Final X3 copies all 217 source classes locally and
has no shared game-class exception. Its pinned launcher and per-slot static
lifecycle preserve the fixed login and self-character packet readers.

`HSOAutoTradeSupport` is the compatibility hook facade. Its method names and
descriptors remain patcher ABI, while `LunaSemanticHooks` dispatches menu/frame
events into the semantic runtime. `LunaGeneratedLegacyHooks` is a generated
binary compatibility bridge for behavior that must remain compatible with the
golden client; it is never a feature API and is not edited by hand. New
behavior and policy belong in `src/luna`.

`HSOAutoTradeSupport` remains the compatibility hook façade while extraction
is no longer the source-edit boundary. Its method names and descriptors remain
patcher ABI. New behavior
belongs in the semantic controllers; the generated façade is the only place
allowed to translate hook calls to binary symbols. Until a hook has been
migrated, the old generated support body is treated as a legacy boundary and
must be covered by the existing binary tests.

## Symbol policy

The catalog is versioned by JAR. A symbol lookup is always
`owner + member + descriptor`; a short obfuscated name alone is not a valid
lookup. If the verifier cannot prove an exact match, the patch must stop.
Feature source and architecture documents must not contain binary names.

Private binary fields are accessed through a generated accessor/bridge. A
compile-only JAR may expose an accessor for a specific build, but it is never
the feature API and it must not leak into semantic source.

The Bạch Hổ upgrade experiment was rolled back on 2026-10-04 after user
rejection. Its retained source is not wired into the restored release or the
standard legacy template. The experimental pipeline adds an optional `BachHoClientHooks` facade and
generates its adapter from `tools/luna/BachHoAutomationBridge.java.in` after
catalog verification. It patches the current endpoint input without rebuilding
legacy PetFeed support. Saved-position combat shares native auto intent, yields
to trade/N4, and acquires movement before issuing a return path. Its recovery
ownership query and native rearm guard keep the existing coordinators from
resetting a return path. The adapter's route-ownership check reads the original
trade/N4 states directly so ownership queries cannot recurse.

## Invariants

1. The baseline JAR is never overwritten; every output uses a new name.
2. MIDP 2.1 / CLDC 1.1 compatibility is mandatory.
3. Support code is compiled with UTF-8 and `--release 7`.
4. No Java 8 API, lambda, stream, or large per-frame allocation is allowed.
5. AutoTrade and N4 never write a path without `MovementArbiter` ownership.
6. A pet is not declared lost while a map or dialog is still settling.
7. Resources outside the declared allow-list remain byte-identical.
8. A failed symbol, hook-count, class-version, or `-Xverify:all` check blocks release.

## Migration rule

Move one behavior at a time from the legacy hook into a semantic controller.
For each move, preserve the hook descriptor, add a fixture, run host tests,
patch a new JAR, compare resources, verify class versions, and record the
output hash in the version matrix. Rollback is always the unchanged
`luna_final.jar` golden.
