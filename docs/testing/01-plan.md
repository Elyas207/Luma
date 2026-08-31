# Testing Stage 1 — risk register and plan

## 1A. Risk register

Ranked by likelihood × user impact. "Detectable today" means: would anything in the repo
catch it before a user did?

| # | Risk | Likelihood | Impact | Detectable today | How to test |
|---|---|---|---|---|---|
| R1 | Stream stops partway through every track (un-attested stream cap) | **happened** | fatal | no → **now yes** (device oracle) | `playback.mjs sustained` — 90s of continuous audio |
| R2 | First play after install never starts | **happened** | fatal on day one | no | cold-install device oracle |
| R3 | Resolver hands back a dead URL from cache after a 403 | high | track dies mid-play | no | unit: cache invalidation on 403 |
| R4 | Audio focus lost and never regained (call, nav prompt) | high | silence until relaunch | no → **now yes** | `playback.mjs focus` |
| R5 | Event log loses or duplicates events across a crash | high (once built) | corrupts the whole model | n/a | harness: kill mid-write, replay |
| R6 | **Provenance mislabelled** — autoplay logged as `manual_browse` | high | model learns from itself, silently, forever | n/a | harness assertion on every emitted event |
| R7 | Playback state desyncs from the engine on rapid input | medium | stuck UI | no | fake engine + rapid command sequence |
| R8 | Process death mid-playback loses resume position | medium | user restarts a 2-hour recitation | no | lifecycle test with saved-state |
| R9 | Clock change / DST corrupts decay and context buckets | medium | silent model corruption | n/a | injectable clock, jump time |
| R10 | Skin palette replaced by artwork, text unreadable | **happened** | screen unusable | no → **now yes** | `verify.mjs skins-hold` |
| R11 | Downloads not playable offline | medium | core promise broken | no | offline transport fake |
| R12 | Queue mutation while playing (remove current) crashes or dead-airs | medium | playback stops | no | integration on queue ops |
| R13 | Search results arrive out of order after fast typing | medium | wrong results shown | no | controllable network, ordered responses |
| R14 | Max font size breaks layout | medium | unusable for some users | no | UI test at 2.0 font scale |
| R15 | Soft suppression never expires | low | permanent wrong inference | n/a | harness, 90-day clock jump |
| R16 | 90-day pruning deletes too much | low | history loss | n/a | harness boundary test |
| R17 | Schema migration drops the profile on update | low | total loss | partial (Room schemas committed) | migration test 38→39 |
| R18 | Private session still writes events | low | trust breach | n/a | harness: assert zero rows |

## 1B. Test plan — tier per risk

- **Unit** (JVM, no device): R3, R9, R15, R16, R17, R18, plus evidence-extraction and
  affinity maths.
- **Harness** (replay over synthetic logs): R5, R6, R15, R16, R18 and every personalisation
  scenario in the failure catalogue.
- **Integration** (Robolectric + fakes): R7, R8, R12, R13.
- **Device oracle** (real emulator, already built): R1, R2, R4, R10.
- **Device-manual** (`02-device-script.md`): real call, real Bluetooth, real car, Maps
  ducking, battery over an hour — none of these are honestly automatable here.

**Assertion style**: every test asserts observable state (a playhead moved, a row exists,
a decision changed), never that a mock was called. Every test is written to fail first —
the code is broken deliberately, the red is observed, then restored.

## 1C. Performance budgets

Proposed numbers and how each is measured. Numbers are targets to be *measured against*,
not claims.

| Budget | Target | Measurement |
|---|---|---|
| Cold start → interactive | < 2.0 s | `am start -W` `TotalTime`, median of 5, after `pm clear` |
| Time to first audio from tap | < 6 s | media-session position > 0, from the playback oracle's timestamps |
| Autoplay decision | < 50 ms | instrumented timer around the selection pipeline, logged |
| Search response rendered | < 1.5 s | UI text present after keyevent, from the driver |
| Scroll frame budget | 60 fps, no frame > 32 ms | `dumpsys gfxinfo <pkg> framestats` over a scripted fling |
| Memory after 2 h session | < 300 MB PSS | `dumpsys meminfo` |
| Car Mode battery | ≤ 6 %/h screen-on | battery stats delta, device-manual only |

**Honest limitation recorded up front**: this environment has two x86_64 emulators and no
physical device. Frame timings and battery figures from an emulator are not
representative of a mid-range Android phone. Anything measured here is labelled
"emulator" in the final report and the mid-range gate is marked **untested**, not passed.
