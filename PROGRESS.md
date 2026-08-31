# PROGRESS

Autonomous run started 2026-08-30. Branch `autonomous/2026-08-30`.
Read this file first on resume; do not redo completed work.

## Current stage

**Complete.** Three refinement passes run; see `FINAL-REPORT.md` for what landed and what did not.

## Done

| Step | Commit | Note |
|---|---|---|
| Safed the prior session's uncommitted work (290 files) | `533b8aa33` | playback root-cause fixes, skin integrity, verification harness |
| Read all four prompts + reference image folder listing | — | all present, none missing |
| Stage 0 recon → `docs/audit/00-inventory.md` | pending commit | screens, nav graph, components, measured token sprawl, tap depth, test-infra state |
| `DECISIONS.md` created with D1–D7 + 4 assumptions to review | pending commit | |

## Key facts established in recon

- Token sprawl is **radius and elevation**, not colour: 17 distinct corner radii across 92
  uses, and **zero** elevation/shadow usage anywhere. Colour and type are already
  centralised (36 literal colour uses left in 589 files).
- **5** dialog base implementations and **2** live action-sheet systems.
- Settings / History / Statistics / Appearance / Your listening / Handoff / Car Mode are
  reachable **only** via the overflow on the search-results screen — 4 taps, and not at all
  from Home, Library or the player.
- On a populated Home, the `Your library` / `Search` / `Car mode` links sit below the
  carousel: **4 swipes** to reach.
- An existing `TasteEngine` + `TasteCentreScreen` implement suppression with **no event
  log underneath** — a partial Phase 2 with no Phase 0.
- Asset library is **1.3 GB / 1284 PNGs**; the theme's own gate caps a shipped image at
  400 KB.

## In progress

Nothing. The run finished at delivery; `FINAL-REPORT.md` §2 lists everything unfinished
and §7 the order I would pick it up in.

## Landed since Stage 1

| Step | Commit | Note |
|---|---|---|
| Stage 1 docs | `36a8e27ff` | 41 UX findings, theme study, testing plan, intelligence reconciliation |
| Batch 1 tokens | `da69eb12a` | radius + depth + intensity scales; 89 radius literals migrated; `radius-scale` check |
| Batch 2 navigation | `6c3ddb3ee` | all 8 destinations 1 tap from Home (was 4); Taste Centre inset + gutter |
| Car Mode ornament | (in `abc165154` range) | intensity honoured by `SkinOrnamentLayer`; no photography in Car Mode on any skin |
| Statistics | `abc165154` | grid widened 200→320dp (clipping), duration truncated to seconds |

**Branch pushed** to `origin/autonomous/2026-08-30` and verified on the remote.

### Findings status so far
Fixed: 5, 16, 28, 29, 31, 32, 41. Rejected (my error): 17, replaced by 17b.
Open: the rest.

## Blocked

Nothing blocked.

## Findings still open

None recorded yet — Stage 1 produces the first set.

## Notes for whoever resumes this

- Build: `sh gradlew assembleGithubUniversalProdDebug` (the `sh` prefix is required; the
  working tree is on exfat and has no exec bit).
- Tests: `sh gradlew :composeApp:testGithubUniversalProdDebugUnitTest`.
- Device oracles: `node tools/unlazy-checks/verify.mjs <check>` and
  `node tools/unlazy-checks/playback.mjs <plays|sustained|transport|focus> <serial>`.
- Emulators: `emulator-5556` phone, `emulator-5554` tablet. A playback test needs
  `pm clear` first *and* `pm grant … POST_NOTIFICATIONS` afterwards, or the media
  foreground service cannot start.
