# Stage 0 — Recon inventory

Facts only. Measured on 2026-08-30 against commit `533b8aa33`, on emulator-5556
(phone, 1080x2400) and emulator-5554 (tablet, 2560x1600). No opinions here; findings
live in `01-ux-findings.md`.

## 0. Platform

- **Framework**: Kotlin Multiplatform + Jetpack Compose. Android is the only shipping
  target (`composeApp/src/androidMain`); `commonMain` holds the database and small
  utilities, `desktopMain` exists but is not a product surface.
- **Screens live in**: `composeApp/src/androidMain/kotlin/app/kreate/android/themed/**`
  (the newer Luma surfaces) and `composeApp/src/androidMain/kotlin/it/fast4x/rimusic/ui/screens/**`
  (inherited upstream surfaces). Both are live; this split is itself a finding.
- **Design token file**: yes — `app/kreate/android/themed/luma/LumaDesign.kt`
  (`LumaColor`, `LumaType`, `LumaShape`, `LumaMotion`) plus a ten-skin layer at
  `app/kreate/android/themed/skin/`. Determined by reading the files, not by guessing:
  `LumaColor` roles derive from the active `ColorPalette` via Compose state, and
  `SyncLumaPalette(appearance.colorPalette)` in `MainActivity` is what keeps them current.
- **Storage**: Room, `app.kreate.database.AppDatabase`, schema version 38
  (`composeApp/schemas/`).

## 1. Screen inventory

Route enum: `it/fast4x/rimusic/enums/NavRoutes.kt`.

| Route | File | User is trying to | Most important action |
|---|---|---|---|
| `home` | `themed/now/NowScreen.kt` | Get back into what they were listening to | Resume the hero item |
| `library` | `themed/library/LibraryScreen.kt` | Find something they already saved | Open a saved item |
| `search` | `themed/luma/LumaSearchScreen.kt` | Find something by name | Submit the query |
| `searchResults` | `ui/screens/searchresult/SearchResultScreen.kt` | Pick from matches | Play a result |
| `now` (player) | `themed/player/LumaPlayer.kt` | Control what is playing | Play/pause |
| `queue` | `ui/screens/player/…` | See and reorder what is next | Reorder / jump |
| `carMode` | `themed/car/CarModeScreen.kt` | Operate playback while driving | Play/pause, skip |
| `listening` | `themed/taste/TasteCentreScreen.kt` | See and correct what the app inferred | Undo an inference |
| `handoff` | `themed/handoff/HandoffScreen.kt` | Move playback to another device | Scan / accept |
| `history` | `ui/screens/history/HistoryScreen.kt` | Re-find something recent | Replay an item |
| `statistics` | `ui/screens/statistics/StatisticsScreen.kt` | See listening totals | (browse only) |
| `appearance` | `themed/skin/SkinPickerScreen.kt` | Change the skin | Select a skin |
| `settings` | `ui/screens/settings/SettingsScreen.kt` | Change behaviour | Open a settings group |
| `localPlaylist` | `ui/screens/localplaylist/LocalPlaylistScreen.kt` | Work with a playlist | Play the playlist |
| `mood` / `moodsPage` | `ui/screens/mood/**` | Browse by mood | Open a mood |
| `newAlbums` | `ui/screens/newreleases/NewReleasesScreen.kt` | See new releases | Open an album |
| `podcast` | `ui/screens/podcast/PodcastScreen.kt` | Browse podcasts | Play an episode |
| `artistAlbums` | `themed/common/screens/artist/**` | Browse a creator | Play / follow |
| `profiles` | `ui/screens/profiles/ProfilesScreen.kt` | Switch profile | Select profile |
| `LICENSES` | — | Read licences | (read only) |

Overlays and sheets: the per-song action sheet (`themed/common/component/BottomMenu.kt`),
the legacy menu family (`ui/components/themed/Menu.kt`, `MediaItemMenu.kt`,
`PlayerMenu.kt`, `SortMenu.kt`, grid variants), plus **32 dialog files**.

## 2. Navigation graph — measured on device

Entry: cold open lands on `home`.

- **Home (first run, empty)** exposes text links: `Your library`, `Search`, `Car mode`.
- **Home (populated)** shows the hero + "Back to" carousel first. The same three links
  still exist but sit **below the carousel**: reaching them took **4 full swipes** on a
  1080x2400 phone. Above the fold, the only affordances are the hero `Play` and a
  `Search` icon (`content-desc="Search"`; the word "Search" is not rendered as text
  there, which is why a text-only driver misses it).
- **Settings, History, Statistics, Appearance, Your listening, Continue on another
  device, Car Mode** are reachable **only** through the overflow (`content-desc="More"`)
  on the **search results** screen. There is no route to them from Home, Library or the
  player. Verified by dumping the view hierarchy on each screen.
- Back behaviour: from Search, back returns to Home; a second back leaves the app.
  From the player, back returns to the previous list. No dead ends were found — every
  destination rendered content and none trapped the user (7 destinations × 2 devices,
  zero crash logs).

**Dead ends**: none found.
**Screens with no back affordance**: none found.
**Screens 3+ taps deep**: Settings (4), Your listening (4), History (4), Statistics (4),
Appearance (4), Handoff (4) — all via search → results → More → item.

## 3. Component inventory

| Job | Implementations | Note |
|---|---|---|
| Dialog | **5** distinct base implementations: `themed/common/component/dialog/Dialog.kt`, `ui/components/tab/toolbar/Dialog.kt`, `ui/components/themed/Dialog.kt`, `ui/components/themed/IDialog.kt`, `me/knighthat/component/dialog/Dialog.kt` | plus 32 concrete dialog files |
| Action sheet / menu | **2** live systems: `themed/common/component/BottomMenu.kt` (Material3 `ListItem`) and `ui/components/themed/Menu.kt` + `MediaItemMenu`/`PlayerMenu`/grid variants | both reachable in normal use |
| Player | **2**: `themed/player/LumaPlayer.kt` (current) and `ui/screens/player/Player.kt` (legacy, still compiled) | |
| List row | `themed/rimusic/component/song/SongItem.kt` (canonical) + ad-hoc rows in several screens | |
| Header | `ui/components/Skeleton.kt` section switcher + per-screen headers | |
| Empty state | `themed/common/component/EmptyState.kt` (added this session) | not yet used everywhere |

## 4. Design token reality check — counted, not estimated

Scanned 589 Kotlin files under `composeApp/src/androidMain/kotlin`:

| Token class | Distinct values | Total uses |
|---|---|---|
| ARGB colour literals | **32** | 36 |
| Font sizes (`fontSize = N.sp`) | **16** | 22 |
| Font weights | 5 (`Medium` 17, `Normal` 11, `Bold` 8, `Light` 2, `SemiBold` 2) | 40 |
| Corner radii (`RoundedCornerShape(N.dp`) | **17** | 92 |
| Padding singletons (`padding(N.dp)`) | 13 | 48 |
| Elevation / shadow | **0** | 0 |

Radii by frequency: `8dp`(28) `16dp`(15) `4dp`(6) `12dp`(6) `20dp`(5) `24dp`(5) `2dp`(5)
`15dp`(4) `5dp`(4) `10dp`(4) `18dp`(3) `3dp`(2) … 17 values total.

Colour and type are already mostly centralised (`LumaColor`/`LumaType` — only 36 literal
colour uses remain across the whole app). **Radius and elevation are not**: 17 radii with
no scale, and no elevation system at all.

## 5. Tap-depth table — from cold app open

Measured on the populated-home state, which is the state a returning user sees.

| Action | Taps | Route |
|---|---|---|
| Play last item | **1** | hero `Play` |
| Resume | **1** | same control |
| Search | **1** | search icon (top right) |
| Open Now Playing | **1** | tap hero / mini-player |
| Open queue | **2** | player → `QUEUE` |
| Favourite current item | **2** | player → `LOVE` |
| Reach Car Mode | **1 tap + 4 swipes** | bottom of Home |
| Reach personalisation ("Your listening") | **4** | search → results → More → item |
| Reach downloads | **1 tap + 4 swipes + 1** | Home bottom → Library → Downloads |

## 6. Test infrastructure state

- **Framework**: JUnit4 + Robolectric (`composeApp/build.gradle.kts` lines 110–111).
  No mockk, no Turbine, no Compose UI test dependency, no instrumentation tests.
- **Test files**: 9 (7 in `androidUnitTest`, 2 in `commonTest`), 61 test cases total.
- **One command**: `sh gradlew :composeApp:testGithubUniversalProdDebugUnitTest`.
  (`sh` is required — the filesystem is exfat and carries no exec bit.)
- **CI**: `.github/workflows/` exists for build; no test gate on PR.

## 7. Existing intelligence-adjacent code

- `service/taste/TasteEngine.kt` (189 lines): an in-memory suppression register with
  `recordDeparture`, `recordQueueRemoval`, `overrideAsLiked`, `forget`, `forgetAll`.
- `themed/taste/TasteCentreScreen.kt`: a "what was learned" surface.
- No event log, no facet model, no affinity cells, no provenance, no decision log.
