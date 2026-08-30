# Kreate fork — improvement plan

Fork of `knighthat/Kreate` at `f02577e86`. Upstream remote is configured; rebase onto upstream periodically.

Target devices: phone (portrait) + Android tablet used as a car centre display (landscape).

---

## Diagnosis

All findings below are read off the code at the commit above, with file:line references.

### The app has four stacked generations of code

`it.fast4x.rimusic` (RiMusic origin) → `me.knighthat` (fork author) → `app.kreate` (current
rewrite) → `com.metrolist` (vendored from Metrolist). 729 Kotlin files. `Player.kt` is 114 KB,
`Lyrics.kt` 148 KB, `Dialog.kt` 112 KB, `Preferences.kt` 96 KB. Much of the inconsistency the user
sees is these generations disagreeing with each other on-screen.

`NavRoutes` still carries `games`, `gamePacman`, `gameSnake` — dead weight in the top-level IA.

### Video does not go through the app's player at all

Video is a YouTube **IFrame embed in a WebView** (`YoutubePlayer.kt`, `androidyoutubeplayer:13.0.0`),
completely separate from ExoPlayer. Confirmed defects:

| # | Defect | Location |
|---|---|---|
| V1 | `AndroidView` has only a `factory`, no `update` and no `key`. `ytVideoId` is captured in the factory closure, which runs once. **Changing track does not reload the video** — you keep watching the previous one. | `YoutubePlayer.kt:65-112` |
| V2 | `lifecycleOwner.lifecycle.addObserver(this)` inside the factory, never removed. No `DisposableEffect`, no `onRelease`, no `release()`. **WebView + observer leak on every open.** Matters most in long car sessions. | `YoutubePlayer.kt:94` |
| V3 | `IFramePlayerOptions` sets no `origin`. Embed-restricted videos fail. Embed-*disabled* videos can never play at all — architectural, not a bug. | `YoutubePlayer.kt:72-74` |
| V4 | `onCurrentSecond` is wired to `{}`. Progress bar, scrubbing, notification and queue position all desync from the video. | `MainActivity.kt:764` |
| V5 | `Player.playVideo()` is `setMediaItem(); pause()` — **no `prepare()`**. ExoPlayer sits unprepared holding the item while the WebView plays. Duration/position unknown to the rest of the app. | `Player.kt:115-118` |
| V6 | `isVideo` is a `mediaMetadata.extras` boolean set **only** in `Innertube.VideoItem.asMediaItem`. Items rebuilt from the DB via `Song.asMediaItem` lose it. Same content shows video sometimes and not others. | `Utils.kt:158, 225`; `MainActivity.kt:756, 805` |

### Audio stream resolution never recovers from a bad URL

| # | Defect | Location |
|---|---|---|
| A1 | `onPlayerError` contains `// TODO: Add additional recovery step` and does nothing but toast, then optionally skip track. No cache invalidation, no client demotion, no re-prepare. | `ExoPlayerListener.kt:204-219` |
| A2 | `YTPlayerUtils.markWebRemixFailed` / `clearWebRemixFailures` exist precisely to demote a 403'ing WEB_REMIX URL — but are only called from `modules/metrolist/app/.../MusicService.kt:3200,3217`, and `settings.gradle.kts:38` includes only `modules/metrolist/innertube`. **That module never compiles into the app. The self-heal is dead code.** | `YTPlayerUtils.kt:57-74` |
| A3 | MAIN_CLIENT `WEB_REMIX` deliberately **skips HEAD validation** and hands ExoPlayer an unvalidated URL, relying on A2 to demote it on failure. With A2 dead, every retry re-picks WEB_REMIX and 403s identically. This is the "doesn't work a lot of the time" loop. | `YTPlayerUtils.kt:455-462` |
| A4 | Stream-URL cache expiry compares a **duration to epoch millis** (`cache.streamExpiresInSeconds.seconds - 30.seconds` vs `System.currentTimeMillis()`), always true → cache is dead, every resolve refetches. ⚠️ **Do not fix alone.** This dead check is currently the only thing forcing a re-resolve; fixing it without A1–A3 pins every retry to the same dead URL and makes playback strictly worse. | `InnertubeResolvingDataSource.kt:305-315` |
| A5 | `resolveInnertubeMedia` → `getPlayableUrl` → `runBlocking(Dispatchers.IO)` on ExoPlayer's load thread. Full multi-client fallback + PoToken WebView + NewPipe fetch run inside it, uncancellable and unbounded. Skipping a track leaves the old resolution running and holding the thread. | `InnertubeResolvingDataSource.kt:298`, `PlayerModule.kt:92-106` |
| A6 | `ErrorHandlingPolicy.isEligibleForFallback` returns `true` unconditionally with `// TODO: Inspect error`. | `ErrorHandlingPolicy.kt:13` |
| A7 | No custom `LoadControl` on the `ExoPlayer.Builder`. Default buffers are tuned for good connectivity; a car on mobile data wants a much deeper forward buffer and a back-buffer so re-seeks don't refetch. | `PlayerModule.kt:148-156` |
| A8 | `makeStreamCache()` is unreachable dead code — it decodes `JsonWriter.string(Any())`. ~70 lines of misleading noise in the middle of the live resolution file. | `InnertubeResolvingDataSource.kt:229-296` |

---

## Plan

### Phase 0 — Baseline
Build green on this machine, then capture a `logcat` of a failing song and a failing video.

**There is no physical test device.** Verification runs on emulator AVDs: `car_tablet` (Pixel
Tablet, landscape — the Car Mode target) and `phone` (Pixel 7). KVM is available, so these are
hardware-accelerated. The emulator console shapes the network (`network speed edge`,
`network delay gprs`), which is how the slow-connection and interruption cases get exercised on
demand rather than by waiting for a real dead spot.

What that rig **cannot** establish, and must not be reported as verified: Car Mode touch ergonomics
at arm's length while moving, real cellular behaviour, and audio routing / media-session
presentation on the actual head unit. Those go to the user as an explicit checklist to run against a
sideloaded debug APK.

Build command (exfat has no exec bit, so `sh gradlew`; Gradle cache lives off the home SSD):

```
ANDROID_HOME=$HOME/Android/Sdk sh gradlew -g /media/elyas/Seagate/gradle-home :composeApp:assembleDebug
```

### Phase 1 — Streaming and video reliability

Ordered so each step is independently verifiable.

1. **A1+A2+A3 together** — real recovery in `ExoPlayerListener.onPlayerError`: on an HTTP 403/410
   for a resolved remote URL, `clearCachedStreamUrlOf(mediaId)` + `markWebRemixFailed(mediaId)` +
   re-prepare once. Skip to next track only after recovery has already failed. This closes the loop
   that A3 opens.
2. **A4** — correct the expiry check to an absolute deadline stamped at resolve time. Only after
   step 1 is in, for the reason noted above.
3. **A5** — make resolution cancellable and time-boxed; dedupe concurrent resolutions of the same
   video id so a double-tap doesn't fan out into two full fallback sweeps.
4. **A7** — tuned `LoadControl`: deep forward buffer, real back-buffer, prioritise time over size.
   This is what makes a tunnel or a dead spot survivable.
5. **A6** — classify errors properly instead of `return true`.
6. **A8** — delete the dead `makeStreamCache`.
7. **V1–V6** — move video onto ExoPlayer. **Decided** — chosen over patching the IFrame WebView in
   place, so the WebView path gets retired rather than maintained.

   `findFormat()` currently filters `it.isAudio` and discards
   the video formats that are already in the same `adaptiveFormats` response. Select a video format
   alongside the audio one, combine with `MergingMediaSource`, render into a Compose `SurfaceView`.
   Retire the IFrame WebView.

   This inherits, for free: the multi-client fallback resilience, the existing disk cache, the
   MediaSession/notification/queue, correct position and duration, playback of embed-disabled
   videos, no WebView leak, and a video surface that Car Mode can actually use.

   `isVideo` moves off `mediaMetadata.extras` and onto the persisted song record so it survives a
   round trip through the database.

   Verified groundwork: `PlayerResponse.StreamingData.Format`
   (`modules/metrolist/innertube/.../models/response/PlayerResponse.kt:45-78`) already carries
   `width`, `height`, `fps` and `qualityLabel`, and defines `isAudio = width == null`. So a
   `findVideoFormat()` mirrors the existing `findFormat()`, its URL goes through the same
   `findUrlOrNull` + n-transform + `pot=` pipeline, and it needs a second cache key
   (`videoId:video`) threaded through `resolveInnertubeMedia`. No new network path, no new fallback
   logic.

8. **Loading and error UX** — a single shared playback-state surface, so every screen shows the same
   thing. Researched patterns, applied deliberately:
   - Non-blocking inline pill over still-visible content for a recoverable error, rather than a
     modal that ends the session ([Grok](https://mobbin.com/screens/fe248bef-19ec-4f49-b83b-0096463a9c65)).
     This is the correct default for Car Mode — never a dialog while driving.
   - Centred title + one-line cause + a single large **Try again** for a terminal error
     ([Prime Video](https://mobbin.com/screens/7f93f562-2680-4876-9cc2-c11898ae4092),
     [Finch](https://mobbin.com/screens/eaebf4be-46a2-4769-b6d9-189dd755756e)).
   - Skeleton artwork + metadata rather than a spinner on a blank screen, so layout doesn't jump
     when content lands.

### Phase 2 — Car Tablet Mode

A separate surface, not a stretched tablet layout. Entered explicitly and exited explicitly.

Layout, landscape, three zones, no nesting deeper than one level:

- **Left**: large artwork and metadata — the "what's playing" anchor, readable at arm's length.
- **Centre/bottom**: one persistent transport row. Previous / play-pause / next at the largest touch
  size in the app, plus a scrubber. Always visible, never scrolls away. The
  [Tesla Robotaxi](https://mobbin.com/screens/9a7ec56d-2669-43b3-a4e4-8b6daa2a58e6) pattern —
  paired stepper controls in one always-present row, no menus — is the reference for
  glanceable-at-speed density.
- **Right**: up-next queue as a list, tap-to-jump, no drag required while moving. The
  [Hulu](https://mobbin.com/screens/3d7f29ec-07da-4edc-8174-4a5f21eb762b) player-over-queue split
  and [Particle News](https://mobbin.com/screens/9065ab0a-5260-47f0-9fc5-3e44bd42fa12)
  "Now Playing / Playing Next" grouping are the references.

Rules for this mode:
- Minimum touch target 64 dp, primary transport larger.
- One tap to every common action from the home screen. Nothing important more than two taps deep.
- No modal dialogs, no toasts that require dismissal, no text below ~18 sp.
- Recents and favourites on the home screen as large tiles — the "change music without digging"
  requirement.
- Audio ⇄ video is a single toggle, and it does not restart playback (this is only possible once
  Phase 1 step 7 lands and both share one ExoPlayer).
- Degraded network shows a persistent inline status, and playback keeps whatever is buffered.

### Phase 3 — UI/IA across the app

Not a repaint. In order:
1. Establish one spacing/type/touch-target scale and one component set; the four code generations
   currently each bring their own.
2. Cut the top-level IA down to what earns its place (the games routes go).
3. Rework settings — currently a very long flat surface — into grouped sections with search.
4. Consistent empty / loading / error states, from the same shared surface built in Phase 1 step 8.
5. Screen-by-screen pass: home, search, browse, playlist, artist, album, queue.

### Phase 4 — Mobile
Thumb-reach audit of the bottom navigation and player controls, sheet behaviour, and per-breakpoint
layout decisions rather than one layout scaled to fit.

---

## Honest scoping

Phases 1 and 2 are well-defined and I can carry them to a verified, working state.

Phase 3 as written — "go through the application carefully", every screen, every state, across four
inherited code generations and 729 files — is genuinely multi-session work. I will not claim it is
done in one pass. It is sequenced above so each step ships something coherent on its own rather than
leaving the app half-restyled.

---

## Progress

_Updated 2026-08-28. Verified on `car_tablet` (2560x1600) and `phone` (1080x2400) emulators._

### Environment (was blocking everything)

- **AVDs must live on ext4, not the Seagate.** The emulator acquires its AVD lock with `link()`;
  exfat has no hard links, so it returned `EPERM` forever and the emulator hung with no error.
  Moved to `kreate-build/avd` — boots in 15s. Gradle cache moved to NVMe too: full build 1h16m → ~1m.
- `kotlin.daemon.jvmargs=-Xmx6144M` added; the Kotlin daemon does not inherit `org.gradle.jvmargs`.

### Phase 1 — complete

| Item | State |
|---|---|
| A1–A3 recovery loop | done, compiles; not yet triggered on a live 403 |
| A4 absolute expiry deadline | done, 4 tests |
| A5 off-load-thread, deduped, 45s cap | done |
| A6 error classification | done, 3 tests |
| A7 car-tuned `LoadControl` | **verified: 45s of total network loss with no stall** |
| A8 dead `makeStreamCache` deleted | done |
| V1–V6 video onto ExoPlayer | done; WebView + dependency removed; DB v37 `songs.is_video` |

Also found and fixed while testing, neither in the original diagnosis:
- **Infinite recomposition on any playback error** (`Thumbnail.kt`) — state written during
  composition. 986 error passes in 28s from one tap, now 2.
- **`visitorData` was a User-Agent string**, so every request looked forged to YouTube and the
  "fetch a real token" branch never ran. Real `Cg…` tokens now minted and persisted.

Playback confirmed working end-to-end on the emulator: `WEB_REMIX` rejected → fallback swept →
`ANDROID_VR` validated and played.

### Phase 2 — Car Mode built and verified

Three-zone landscape surface, entered from the overflow menu, exits back to the app.
`CarDimensions` / `CarTransport` / `CarArtwork` + `CarTrackInfo` / `CarQueue` / `CarBrowse`.

- Touch targets measured on-device: play 112dp, skip 88dp, exit 64dp, tiles 180x240dp.
- Browse: "Jump back in" + "Favourites" shelves, one tap to play.
- Audio/video toggle — a surface swap, no restart, only possible because both share one ExoPlayer.
- Offline banner wired to `ConnectivityUtils`, inline and non-blocking.
- 7 unit tests assert the car-safety rules so they cannot silently regress.

Layout bugs found only by running it: artwork sized from width (pushed transport off-screen),
transport clipped by the system taskbar, tile artist line clipped, and the normal player sheet
opening *over* Car Mode.

### Phase 3 — started

- Games removed (13 files, ~1,500 lines). The long-press easter egg was also an accidental-trigger
  hazard.
- **Toolbar: 14 unlabelled icons → 4** (Sort, Search, Shuffle, overflow). The overflow renders
  labelled text. Bulk/destructive actions demoted out of prime space.
- Shared `EmptyState` (icon → headline → explanation → action), wired per song category.
- **Fixed: infinite skeleton placeholders.** `isLoading` latched true whenever a category resolved
  to a list equal to the previous one — two empty categories in a row was enough, which is what a
  new user hits first.

### Also fixed while auditing screens

- **Player title/artist were unreadable on bright covers.** The palette derives light text assuming
  a dark backdrop, but the backdrop *is* the blurred artwork — near-white text on a near-white
  blur. Blurring a bright image only yields a bright image, so a scrim now sits between cover and
  content. Verified against a near worst-case bright sleeve.
- **Car Mode on a portrait phone was broken** — the 60/40 landscape split starved both columns so
  badly that "NOW PLAYING" wrapped to one letter per line and the skip button left the screen.
  Portrait now stacks instead of squeezing.
- Toolbar left-aligned and grouped so it lines up with the tab title and filter chips.

### Not done

- Phase 3: settings reorganisation, screen-by-screen pass beyond the songs library.
- Phase 4: mobile pass is verification-only so far, no thumb-reach redesign.
- Car Mode: gesture interactions.
- A1–A3 recovery has never been exercised against a real 403 — needs a stale URL to occur naturally.

---

## Redesign pass (skins, learning, handoff)

### Design work

`DESIGN.md` holds the product thinking this pass was built against — the three reasons the app gets
opened, what becomes annoying after months, what the app may and may not learn, and why the car is
a separate surface rather than a breakpoint.

### Skins — 10, differing by more than colour

`themed/skin/` adds material, motion, shape and ornament tokens on top of the existing palette,
because ten palettes would have produced ten versions of the same app. Aurora (Frutiger Aero) is a
real sky photo with a depth wash, glossy lozenges and drifting bubbles; Obsidian is pure black with
hairlines and no bounce; Graphite is a machined plate; Vinyl is cream paper with pebble corners.

- Assets curated from 1.3 GB down to **42 KB** (one sky + two bubbles, WebP).
- Picker renders a *miniature real interface* per skin rather than a colour chip, so material is
  visible before committing. Applies live.
- 7 tests pin the car-safety rules; skins cannot violate them.

### Learning — `listening_signals`, DB v38

The premise is that **a skip is not a skip**: leaving at 4 seconds is a rejection, leaving at 80% is
"heard it". Recorded via `onPositionDiscontinuity`, which is the only callback carrying the *old*
position.

Three rules make it shippable: reordering only (never hides anything from library or search), three
strikes before anything changes, and every stored value renders as a sentence. Wired into autoplay,
and surfaced in "Your listening" where any decision can be undone in one tap.

Verified on device: suppression 3 → 2 after "I like this", counters retained so the app can still
explain what it saw.

### Handoff — the QR *is* the payload

No pairing, no server, no account, no internet: the queue and position are encoded in the image.
15 tests including a full encode → QR → decode round trip.

That round trip caught a real bug — `MAX_ITEMS = 120` exceeded QR capacity at error-correction
level H and threw at encode time. It would only have failed for users with long queues, in a car
park. Now derived from actual capacity (90) with the reasoning written down.

### Also fixed

- `dropWhile` → `filterNot` in radio: only a *leading* run of duplicates was being stripped, so
  repeats re-entered the queue.
- Player title/artist unreadable on bright covers — scrim added between blurred cover and content.

### Not done in this pass

- **The QR scanner (receiving side) is not built.** The display side is complete and tested; the
  camera half needs CameraX + a permission, and could not be verified on an emulator.
- Motion tokens are defined per skin but only wired into the picker and Car Mode, not yet across
  every screen's transitions.
- Ornaments render in Car Mode and the picker; the main library screens still use flat backgrounds.
- Time-of-day and device-context weighting is designed in `DESIGN.md` but not implemented.

---

## Rename: Kreate → Luma

`APP_NAME` in `composeApp/build.gradle.kts` is the single source of truth, so the launcher label,
crash-log filenames and database export filenames all follow. APK now builds as `Luma-izzy.apk`.

### Reading the mark

Polished chrome italic "L" with three arcs radiating from a dot — motion plus broadcast. Two
properties drove every decision:

- **It is chromatically neutral.** No hue of its own, so it sits on all ten skins without clashing.
- **It is bright.** Mean luminance 183, i.e. silver. On the four *light* skins (Aurora, Vinyl,
  Terrazzo, Bloom) the original washes out until the top of the L disappears.

So the mark ships in two variants — the original for dark skins, and a darkened/contrast-lifted
"graphite" for light ones. Both keep the bevel, so it still reads as machined metal. Flat-tinting
was rejected: the chrome *is* the identity.

The wordmark is now **text**, not artwork. The old `app_logo_text` vector was one fixed colour;
rendering "Luma" in the app's own type lets it inherit the palette and stay legible on every skin.

### Icon

Adaptive icon: chrome foreground on a deep graphite diagonal ramp, whose light direction agrees
with the bevel already in the artwork. Verified under squircle, circular and Android 13 themed
masks — nothing clips in the circular crop. Source trimmed of ~50% dead padding first.

### Two bugs this surfaced

- **`AppBar.contentColor()` branched on the theme-mode *preference*, not the actual palette.** With
  a light skin the mode still read "Dark", so the app bar drew white on white. Now derived from
  `colorPalette()`, which also fixes the same latent bug for cover-derived and Material You
  palettes.
- **`Repository.REPO` was built from `APP_NAME`**, so the rename silently repointed every "report
  an issue", discussions and update-check URL at `knighthat/Luma`, which does not exist. Pinned to
  the real upstream name.

### Deliberately not renamed

The `applicationId` is still `me.knighthat.kreate`, and package names are unchanged. Changing the
application id makes the app a *different* app to Android: existing installs would not update, the
signing identity would not carry, and every `app.kreate.android.*` import would churn against
upstream. The product name and the package id are separate things, and only the first was asked for
— say the word if you want the id migrated too, but it wants its own pass.

---

## Redesign pass 2 — the visual language, and four playback fixes

_2026-08-29. Verified on the `kreate_tablet` AVD (2560x1600, API 34)._

### Why there was a second pass

Pass 1 fixed the IA and the verdict was still "the main menus look like the original with stuff moved
around". That was correct: changing what a screen is *for* does not change what it *looks like*, and
only the second is perceived at a glance. See the new section in `DESIGN.md` for the language —
circles carry focus, the arch is the tile, type is the texture, the screen is lit by the record.

### Done and seen running

| Surface | State |
|---|---|
| `themed/luma/` token layer | type / shape / motion / colour, additive, touches no existing theming |
| Instrument Serif bundled | 141 KB, OFL, `assets/fonts/` — the app had only Poppins and Rubik, both generic sans |
| Home (`NowScreen`) | arch hero, oversized serif headings, artwork-derived atmosphere — **verified** |
| Library | retrofitted to the language; shelves and shape-carries-type kept — **verified** |
| Search | rewritten: no app bar, no tab strip, serif index — **verified, search works end to end** |
| Car Mode | retrofitted; every `CarDimensions` safety size preserved, 7 safety tests still pass — **verified** |
| Player | rewritten around the disc + progress arc + ring transport — **compiles, not seen running** |

### Playback — four fixes, three verified against the live service

1. **`validateStatus` used `HEAD` with the account cookie on every url.** Both wrong. googlevideo
   routinely 403s a `HEAD` for a url that serves a byte-range `GET` perfectly (the MAIN_CLIENT path
   already had a comment saying so and skipped validation to dodge it), and the web cookie was being
   presented to `c=IOS` / `c=ANDROID` urls it does not belong to. Now a one-byte ranged `GET` with
   the cookie only for web-family urls. **Verified: the exact url that returned 403 now returns 206
   and is used.**
2. **Auth-rejected clients are remembered for 10 minutes.** Six of twelve clients answered "Sign in
   to confirm you're not a bot" on *every* track, costing ~5s per resolve for no work.
   **Verified: second resolve skips five clients instantly.**
3. **The thrown error was whichever client happened to be last.** On a track ten clients considered
   playable, the user was told "Video unavailable" because client 12 said so. Now distinguishes
   "nobody will serve this" from "no link survived validation".
4. **Crash fix:** the new artwork-accent extraction passed Coil's `Config.HARDWARE` bitmap to
   `Palette`, which cannot read its pixels — `IllegalStateException`, process down. Software bitmap
   requested, sampling capped, and the whole path wrapped so a decorative colour can never crash the
   app again.

First resolve went from **~19s ending in failure** to **~9s ending in a validated stream**.

### Still broken, and honestly

**Playback still fails at the last hop, and it is not something this codebase can fix cheaply.**

- The stream url that now validates is `c=IOS` and carries **no `pot=` parameter**. PoToken is only
  minted for `WEB_REMIX`. googlevideo serves a 1-byte probe and then 403s the real ranged read.
- `WEB_REMIX` cannot produce a url at all because **the signature cipher is stale**: YouTube is
  serving player `e937390a`, which is not among the 114 bundled configs, the remote config refresh
  URL **404s**, and all seven legacy regex fallbacks fail. The classic
  `a=a.split("")…join("")` signature function no longer exists in that player.

Both are the YouTube streaming-protection arms race — the thing yt-dlp and NewPipe track
continuously. The realistic fixes are (a) repoint the remote config source at something maintained,
and (b) mint a PoToken for whichever client actually wins the fallback, not just `WEB_REMIX`.

### Consequence for the redesign

Home leads with *recently played*, which is driven by playback **events**. With playback broken no
events are ever recorded, so home shows its empty state on a fresh install no matter how good it
looks. The player cannot be seen at all without a playing track. This is why two of the surfaces
above are marked unverified — not because they were skipped.

### Known seam

Search is redesigned; the **search *results*** screen it navigates to is still the old app-bar +
seven-tab chrome. It is the most visible remaining inconsistency in the main journey.

---

## Playback — the full diagnosis, measured against the live service

_2026-08-29, late session. Everything below was verified with real requests, not inferred._

### What was actually wrong (four separate faults, stacked)

1. **The signature cipher was dead.** YouTube rotated to player `e937390a`, for which there is no
   config, the remote config feed 404s, and all seven legacy regex fallbacks miss. Proven not to be
   a pattern problem: the player was instrumented so all **5,106 closure-local functions** were
   reachable, and **none** takes a string and returns a permutation of it. YouTube has moved the
   descrambler inside the player's own interpreter, so static extraction now means reimplementing
   that VM.
   **Fixed** by pinning: YouTube still serves every old player (all 57 configured hashes return
   200), and the `signatureTimestamp` sent with a request selects which player's cipher the server
   signs for. `PlayerJsFetcher` now pins to the newest player we hold a config for whenever the live
   one is unknown. WEB_REMIX deciphers again.

2. **The two proof-of-origin tokens were swapped.** BotGuard mints a token bound to whatever
   identifier it is given; the session-bound one was being sent as the *player request* token and
   the video-bound one appended to the media url. YouTube expects the reverse. A swapped `pot` fails
   silently as a *throttle*, not an error.

3. **`pot` was only attached to web-family clients** — i.e. only the client whose formats need the
   cipher. With the cipher broken, playback always landed on IOS/ANDROID with no `pot` at all.

4. **ExoPlayer asked for the whole file in one request.** The resolver set the full `contentLength`,
   so the first read was `Range: bytes=0-<clen-1>` — up to 1.5 GB for a long recitation.

### The measurements that settled it

Against one live url (`clen` 566,445):

| Request | Result |
|---|---|
| `bytes=0-1` | 206 |
| `bytes=0-450000` | 206 |
| `bytes=0-460000` | **403** |
| whole file | **403** |
| no `Range` header | **403** |

The ceiling is per-request, not per-file: a 496 MB file behaved identically. It is also not the
User-Agent — iOS, Chrome and no-UA all behave the same — and not the client: IOS, ANDROID and
WEB_REMIX all cap the same way. Every non-IOS client is additionally blocked outright
("Sign in to confirm you're not a bot", or "The page needs to be reloaded", which is YouTube's
PoToken-missing signal).

### What is fixed and verified

- Cipher works again (pinning) — WEB_REMIX now produces deciphered urls.
- `validateStatus` no longer lies: it was `HEAD` + the account cookie on every url. googlevideo 403s
  `HEAD` for urls it will serve via ranged GET, and the web cookie does not belong on `c=IOS` urls.
  Now a ranged GET, cookie only for web-family. **The exact url that returned 403 now returns 206.**
- The probe is now *deep* — it asks for a range past the un-attested allowance, so a url that would
  die mid-track is rejected during resolution instead of by the user.
- Auth-rejected clients are remembered for 10 minutes; six of twelve were being re-asked on every
  single track, ~5s per resolve for nothing.
- The error message no longer reports whichever client happened to be last in the list.
- `ChunkedDataSource` splits the fetch into 256 KB ranges while reporting the true length upward.
  **Audio plays**: `state=PLAYING(3), position=5946` — the first time any audio has been decoded.

### What is still wrong

Playback **starts and plays, but does not reliably run a whole track.** The chunked transport is
wired and compiles into the APK, but its continuation has not been observed running — a truncated
entry left in the ExoPlayer cache by an earlier partial download also masked results for several
runs (clear app data when testing this).

The honest remaining problem is proof-of-origin: a *web* PoToken is not accepted on an IOS/ANDROID
url, and WEB_REMIX's own url is still refused, so nothing yet earns an unthrottled stream. The next
step is making the PoToken the CDN will accept — not more client shuffling.

**No Google sign-in is involved in any of this, and none is required.** PoToken is BotGuard
attestation, not authentication.

---

## Final audit pass (gated)

_2026-08-30. Verified against `GATES.md`; 13/13 gates met with recorded evidence._

### The regression this pass existed to fix

The earlier token sweep replaced `colorPalette()` with **fixed dark constants** in ~200 places. That
silently deleted the ten-skin feature: every theme still appeared in settings and none of them
changed a pixel. It was flagged in passing rather than treated as the regression it was.

`LumaColor` now derives its roles from the active `ColorPalette` through Compose state
(`SyncLumaPalette`), and `LumaType` styles are `get()` accessors so they cannot freeze whichever
skin happened to be active when the object was first touched. Proven on device by capturing raw
framebuffers under Obsidian and Terrazzo: mean luma **34.4 vs 223.4**. Home, Library, search
results, the overflow menu, Settings and Car Mode were each re-checked under a light skin, and Car
Mode was additionally checked on Aurora (photographic ornament) at a narrow window.

### Also fixed in this pass

- **History was a dead blank pane** on a fresh install — title, two tabs, nothing else. It now has
  an explicit empty state.
- Two verification oracles were wrong and were corrected rather than loosened: one averaged
  compressed PNG bytes as a brightness proxy (it reported a delta of 1.2 between a near-black and an
  off-white screen); another flagged decorative images for having an empty `contentDescription`,
  which is correct practice. Both now measure what their titles claim, and the theme oracle is
  validated against a negative control.

### Still outstanding

**Playback does not work end to end**, and this pass did not change that. See the playback section
above for the measured diagnosis.


## Playback fixed — the actual root cause (2026-08-30, later)

Playback now plays whole tracks. Recorded here because the failure looked like four different bugs
and was one, and because the thing that finally explained it is not obvious from any log line.

### What was actually wrong

`YouTube.visitorData` — the session token — was minted **once and then reused for ever**. The guard
around it only re-minted when the stored value looked malformed, so an aged token kept being
presented long after YouTube stopped honouring it.

An aged token is answered exactly like a forged one: `"Sign in to confirm you're not a bot"`. That
reply reads as a demand for an account, which is what made this so hard to see — it looks like the
app needs a login, and it does not. It knocked out VISIONOS, ANDROID_VR and TVHTML5 in turn, so the
client sweep fell through to IOS every time.

IOS answers, and its url is un-attested — and **an un-attested googlevideo stream serves only a
fixed fraction of the file.** Measured directly against a live url with `clen=5365234`: 2 KB ranges
at every offset up to 1,078,308 returned 206, and everything from 1,108,040 on returned 403. That
boundary is 20.1% of the file, it did not move when retried 45 seconds later, and it is not a byte
budget — the probes only ever fetched 2 KB each. Which is exactly the 46–58 seconds every track died
at, on a four-minute song.

Proof it was the token and nothing else: the same VISIONOS player request that was refused with a
token lifted from an old log returned `OK` with 23 formats when given a freshly minted one. Sending
or omitting session cookies made no difference in either direction.

The fix is `ensureFreshVisitorToken()` in `InnertubeResolvingDataSource` — one mint per process.

### Measured after the fix

Sustained playback (~90 s of continuous audio, well past the old cap), client `VISIONOS`, transport
reaching offset 4,718,592 on a 4.7 MB track:

- phone, four distinct tracks: 88.7 s / 92.4 s / 89.3 s / 88.9 s — 4/4
- tablet: 88.9 s
- fresh install (`pm clear`), twice: 87.5 s / 88.6 s — 2/2
- pause holds the playhead, resume advances it, driven through the media session
- an incoming call takes focus: 0 ms drift while it rings, then resumes and advances

### Three smaller bugs found on the way, each real

1. **First play after an install never started.** Every warm run passed, so this hid behind them.
   On a fresh install the four restart-triggering preferences (`MAIN_THEME`,
   `NAVIGATION_BAR_POSITION`, `NAVIGATION_BAR_TYPE`, `MINI_PLAYER_TYPE`) are written for the first
   time as the first song starts, and `SharedPreferences` notifies on a *write* even when it stores
   the value already in use — so `MainActivity.recreate()` fired mid-play, stopped the player and
   unbound the service. It now compares against the value the activity was built with, counting an
   unset key as its default, and restarts only on a genuine change.
2. **A logged-out install believed it was signed in.** `YOUTUBE_COOKIES` defaults to `""` and every
   consumer tests it for *null*, so `isLoggedIn` read true, an empty `Cookie:` header went out on
   googlevideo urls and the sweep judged login-required clients as if an account were present.
   Blank now maps to null for cookie, visitorData and dataSyncId alike.
3. **An off-main-thread preference write.** `Preferences.setValue` is `@MainThread`; from anywhere
   else it drops the value *and* raises an "unexpected error" toast at the user. The visitorData
   write ran on ExoPlayer's load thread. It is simply not persisted now — pointless anyway, since
   the token is re-minted every process.

Two transport-layer changes were made while chasing this and are kept because they are correct,
though neither was the cause: a 403 now drops the spent url from the resolver cache so a retry
re-resolves instead of being handed the same dead url (`ChunkedDataSource` → `invalidateStreamUrl`),
and 403 no longer counts as fallback-eligible, since it is a url rotation rather than a verdict on
the track.

### What is still not fixed, and why it costs nothing today

**WEB_REMIX cannot be used.** Its media url is refused at byte 0 with `pot` and `n` stripped as
well as present, so what is rejected is the signature. The live player (`e937390a`, STS 20684) no
longer carries an extractable descrambler — the classic `split("")…join("")` form is absent and the
transform-object shape returns zero matches, because the routine now runs inside the player's own
bytecode interpreter. The app pins to player `edf92b2c` (STS 20629) instead, and the server does not
honour it.

This is free today: VISIONOS needs no cipher and no proof-of-origin and streams whole files.

Its client stanza in `YouTubeClient.kt` was also refreshed (`clientVersion` 0.1 → 1.02, visionOS 1.3
→ 26.5, matching a current yt-dlp) — but that was **not** the fix, and the note is here so nobody
later credits it with one. Tested directly: given a *fresh* visitor token the old 0.1 stanza is
answered `OK` too. It is kept only because the current stanza is offered more formats (23 vs 17).

### One design gap closed

The per-song action sheet still rendered in Material's default sans while the list behind it was
set in the display serif. There are two menu implementations; `BottomMenu.kt` is the one actually
raised for a song, and its `ListItem` headline carried no style at all. Both it and `Menu.kt` now
use `LumaType.Row`.


## Branding and light-skin legibility (2026-08-30, later still)

Two things Elyas caught from screenshots.

### The Kreate "K" in the system media control

The badge Android shows on the media card is the notification's **small icon**, not the launcher
icon — `PlayerServiceModern` sets `R.drawable.app_icon_monochrome`, and that drawable was still
`Kreate-logo-final-round.svg` (the Inkscape docname is right there in
`assets/design/app_icon_monochrome.svg`). Replaced with a solid silhouette of the Luma mark,
generated from the adaptive foreground's alpha at all five densities; notification icons are tinted
by the system, so a silhouette is the correct form.

The legacy `mipmap-*/ic_launcher.png` set was *also* still the purple Kreate "K" — the adaptive
icon (v26+) had been updated during the rename but the legacy PNGs never were. Regenerated from the
same layers (graphite ramp + chrome mark, squircle for `ic_launcher`, circle for
`ic_launcher_round`). That one was not what the media card was showing, but it was wrong.

### Light skins were unreadable while playing

Reported as "the colours are very hard to see in the frutiger aero mode". Two independent causes,
both only visible **while a track was playing**, which is why earlier idle captures looked fine.

1. **The artwork palette overrode the chosen skin.** `COLOR_PALETTE` defaults to
   `ColorPaletteName.Dynamic` and choosing a skin never cleared it, so `setDynamicPalette` replaced
   the skin's colours as soon as audio started — while the skin carried on drawing its own
   backdrop. On Aurora that is a dark palette's pale text over a bright photographic sky. The
   initial palette build already had the rule ("a skin owns the whole appearance"); the artwork path
   and the preference-listener recompute did not. Both now defer to a selected skin. Measured:
   Aurora home while playing went from luma 39.7 to 186.8, against 219.5 idle.
2. **The text scrims were pinned to black.** The home hero faded to 90% `Color.Black` and the arch
   tiles to 86%, which is right under a dark skin and puts near-black text on a near-black base
   under a light one. Both now fade to `LumaColor.Ground`, so the scrim always lands on the same
   side as the `LumaColor.Ink` sitting on it.

Both are now gated (G18, G19), each with a negative control. `themed/skin/`'s Color.White/Black are
gloss and drop shadow rather than backing for text and are deliberately exempt, as are the legacy
player's gradients, which already branch on `lightTheme`/`ColorPaletteMode`.
