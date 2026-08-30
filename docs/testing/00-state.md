# Testing Stage 0 — state of the ground

Facts only, measured 2026-08-30.

## 1. What test infrastructure exists

- **Framework**: JUnit4 + Robolectric. Declared in `composeApp/build.gradle.kts`
  (`libs.junit4`, `libs.robolectric`). No mockk, no Turbine, no coroutines-test, no
  Compose UI test artifact, no instrumentation (`androidTest`) source set at all.
- **Runner / one command**:
  `sh gradlew :composeApp:testGithubUniversalProdDebugUnitTest`
  (the `sh` prefix is mandatory — the working tree is on exfat with no exec bit).
- **Fixtures / mocks**: none. Robolectric is used in one place only, purely to give
  `Uri.parse` a real implementation.
- **CI**: `.github/workflows/` builds the app. **No test gate on pull requests.**

## 2. Honest coverage reality

9 test files, 61 cases. Mapped against the five areas that actually matter for an audio
app:

| Area | Coverage | What exists |
|---|---|---|
| 1. Playback state machine | **none** | no test constructs a player or asserts a state transition |
| 2. Interruptions / audio focus | **none** | `ErrorHandlingPolicyTest` covers *load-error fallback policy*, which is network, not focus |
| 3. Lifecycle | **almost none** | `AppLifecycleTrackerTest` asserts foreground/background counting only |
| 4. Network | **partial** | `ErrorHandlingPolicyTest` (which response codes fall back), `StreamExpiryTest` (url deadline arithmetic), `FindVideoFormatTest` (format selection) |
| 5. Data integrity / intelligence | **none** | there is no event log to test |

Other tests: `HandoffTest`, `HandoffQrTest` (payload encode/decode), `CarDimensionsTest`
(breakpoint arithmetic), `LanguageTest`, `DurationUtlTest`.

**Tests that assert a mock was called rather than behaviour**: none found — there are no
mocks. The existing tests are small and honest; there are simply very few of them, and
none of them touch the failure modes this app actually has.

**The single most important gap**: nothing anywhere asserts that audio plays, keeps
playing, or recovers. The regression that broke every track at 46–58 seconds for weeks
would not have been caught by any test in this repository. It was caught by a device
oracle written by hand (`tools/unlazy-checks/playback.mjs`), which is outside the suite.

## 3. Critical path list, ranked

1. **Cold open → play.** Everything else is decoration if this fails.
2. **Resume** — correct item, correct position, after process death.
3. **Playback survives past the first minute** (the specific historical failure).
4. Search → play.
5. Queue: next/prev, reorder, remove-currently-playing.
6. Interruption → resume (call, nav prompt, focus loss).
7. Car Mode session end-to-end.
8. Download → play offline.
9. Favourite / unfavourite round trip.

## 4. Testability blockers

Listed, not fixed (per the prompt).

1. **`TasteEngine` is an `object`** — a process-wide singleton with mutable state and no
   injection point. Cannot be instantiated per test.
2. **Time is read directly.** `System.currentTimeMillis()` is called at point of use in the
   resolver, the stream-expiry logic and `TasteEngine`. There is no clock seam, so decay,
   session windows and context buckets are untestable by construction.
3. **`Preferences` is a global object** backed by real `SharedPreferences`, and its setter
   is `@MainThread` — a test that writes a preference off the main thread silently does
   nothing (this is a real bug found today, not a hypothetical).
4. **Koin service locator** (`KoinJavaComponent.get`) is called from inside functions
   rather than injected, e.g. `resolveInnertubeMedia`.
5. **Network is not injectable**: OkHttp clients are constructed inside modules; there is
   no fake transport.
6. **The player is created inside a Koin module** with real ExoPlayer and real
   `AudioAttributes`; no `Player` interface seam exists for a fake engine.
7. **Room database** is constructed against a real Android context; no in-memory builder
   is exposed for tests.
8. `MainActivity` is ~1200 lines with palette computation, listener wiring and navigation
   in one composable — untestable without extracting the palette-resolution logic.
