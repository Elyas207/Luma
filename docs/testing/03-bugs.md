# Bugs

Every defect found during the run, with reproduction, expected, actual, severity and suspected
cause. The testing brief asks for these to be logged *before* being fixed; in practice they were
logged in `docs/audit/01-ux-findings.md` as they were found, and this file consolidates the ones
that are genuine defects rather than design findings. Where a bug and a finding are the same thing,
the finding number is given.

Severity: **P0** broken or blocking · **P1** daily friction · **P2** polish.

---

## B1 · Playback stops after 46–58 seconds on every track — P0 · FIXED

**Reproduce.** Play anything. Wait.
**Expected.** The track plays to the end.
**Actual.** Audio stops partway, always between 46 and 58 seconds.

**Cause.** `YouTube.visitorData` was minted once and reused indefinitely. An aged session token is
answered exactly like a forged one — *"Sign in to confirm you're not a bot"* — which knocked out
every client that streams uncapped and forced the resolver down to IOS. An un-attested googlevideo
URL serves only a fixed *fraction* of the file. Measured on a live URL with `clen=5365234`: 2 KB
ranges returned 206 up to offset 1,078,308 and 403 from 1,108,040 onward — a 20.1% boundary that
did not move on retry, and is not a byte budget (each probe fetched 2 KB).

**Fix.** `ensureFreshVisitorToken()` — one mint per process. Verified: 4/4 tracks sustaining ~90 s
on phone and tablet, debug and release builds.

## B2 · Pressing "Love" crashes the whole app — P0 · FIXED · finding 51

**Reproduce.** Play a track, open the player, press **Love**.
**Expected.** The track is favourited and the label changes.
**Actual.** The process dies and the user is dropped to the launcher.

```
java.lang.IllegalStateException: Player is accessed on the wrong thread.
  at androidx.media3.exoplayer.ExoPlayerImpl.verifyApplicationThread
  at app.kreate.android.service.player.StatefulPlayerImpl.getCurrentMediaItem
  at app.kreate.android.themed.player.LumaPlayerKt.SecondaryRow  (LumaPlayer.kt:514)
```

**Cause.** The click handler read `player.currentMediaItem` *inside* `Database.asyncTransaction`,
which runs on Room's transaction executor. Media3 calls `verifyApplicationThread()` on every Player
access; the throw landed in a bare executor with no handler.

**Fix.** Read the id on the calling thread, pass the plain `String` in. Guarded by
`verify.mjs no-player-in-transaction`. A full audit found no other instance of the class.

**Reported by the owner**, not by my testing — see the note in `FINAL-REPORT.md` §4.

## B3 · The first play after a fresh install never starts — P0 · FIXED

**Reproduce.** `pm clear`, launch, search, tap a result.
**Expected.** It plays.
**Actual.** Resolution completes in the log and nothing happens. Every *warm* run works, so this
only ever appears on a genuinely fresh install.

**Cause.** `SharedPreferences` notifies on every *write*, including one that stores the value
already in use. On a fresh install the four restart-triggering preferences are persisted for the
first time exactly as the first song starts, so `MainActivity.recreate()` fired mid-play, stopped
the player and unbound the service.

**Fix.** Compare against the value the activity was built with, counting an unset key as its
default. 0/2 → 2/2 on repeated cold installs.

## B4 · Killing the app mid-track loses your place — P0 · FIXED

**Reproduce.** Play three minutes into a long recitation, force-stop, relaunch.
**Expected.** "Pick up where you left off", at the right position.
**Actual.** "Nothing playing yet." Position and history both gone.

**Cause.** Three separate ones. The queue and playhead were written only on play/pause and timeline
changes, so nothing was saved during ordinary playback; `ENABLE_PERSISTENT_QUEUE` defaulted to
`false`, so none of that ran anyway; and Home's hero reads play *history*, which only gains a row
when a track *ends*.

**Fix.** A 20-second checkpoint while playing, persistence on by default, and Home leads with the
in-progress queue item. Verified end to end on a fresh install.

## B5 · A logged-out install believes it is signed in — P1 · FIXED

**Cause.** `YOUTUBE_COOKIES` defaults to `""` and every consumer tests it for *null*, so
`isLoggedIn` read true, an empty `Cookie:` header went out on googlevideo URLs, and the client sweep
judged login-required clients as though an account were present.
**Fix.** Blank maps to null for cookie, visitorData and dataSyncId alike.

## B6 · An off-main-thread preference write silently fails and toasts an error — P1 · FIXED

**Cause.** `Preferences.setValue` is `@MainThread`; from any other thread it drops the value *and*
raises an "unexpected error" toast at the user. The visitorData write ran on ExoPlayer's load thread.
**Fix.** Not persisted at all — pointless anyway, since the token is re-minted every process.

## B7 · A skin's palette is replaced by artwork during playback — P1 · FIXED

**Reproduce.** Choose a light skin (Aurora). Play something.
**Expected.** The skin keeps its colours.
**Actual.** Text becomes nearly invisible; the app looks broken.

**Cause.** `COLOR_PALETTE` defaults to `Dynamic` and choosing a skin never cleared it, so the
artwork palette replaced the skin's while the skin carried on drawing its own backdrop — a dark
palette's pale text over a bright photographic sky.
**Fix.** Both the artwork path and the preference-listener recompute defer to a selected skin.
Measured: Aurora home 39.7 → 186.8 mean luma while playing.

## B8 · Text scrims pinned to black — P1 · FIXED

Text over artwork is drawn in `LumaColor.Ink`, which flips with the skin, but the scrim behind it
faded to a fixed black — correct on the five dark skins, near-black text on a near-black base on the
five light ones. Now fades to `LumaColor.Ground`. Guarded by `verify.mjs no-fixed-scrims`.

## B9 · Statistics clips titles at both ends — P0 · FIXED · finding 28

`GridCells.Adaptive(200.dp)` gives a 411dp phone two ~205dp columns, which cannot hold artwork,
title, artist, duration and an action. Observed: `Hotel (feat. R.Ke`, `-Z, Boo & Gotti)`. Minimum
raised to 320dp.

## B10 · "Your listening" draws its title under the status bar and its switch over its own text — P0 · FIXED · findings 31, 32

No status-bar inset, and no gutter between the text column and the control.

## B11 · The mini player is drawn under the navigation bar — P1 · FIXED · finding 42

The back, home and recents glyphs sat on top of the artist name. Nothing else on that surface
reserves room for the system bars.

## B12 · Two independent marquees in the mini player — P1 · FIXED · finding 43

Title and artist both scrolled, so at any instant it read as garbled — the title's tail directly
above the artist's middle.

## B13 · The now-playing indicator spills across the mini player's artwork — P1 · FIXED · finding 10

`NowPlayingSongIndicator` defaults its size to the *list-row* thumbnail wherever it is placed, so
over a 44dp disc the equaliser animation rendered as stray blobs on the artwork. Removed there —
the mini player is by definition the playing item, so the badge was redundant.

## B14 · Contrast failures — P0/P1 · FIXED · findings 46, 47, X5

Measured, not eyeballed. The greeting label at **2.90:1** (`LumaLabel` defaulted to `InkFaint`, the
*disabled* role); tablet hero text at **1.97:1** over bright artwork; arch-tile captions at 3.68:1;
library monograms at 2.36:1 after my own first fix went too far. All now pass, worst case 5.22:1 on
tablet and 6.50:1 on phone.

## B15 · An empty session claims perfect focus — P1 · FIXED

`intentEntropy()` returned 0.0 with no data, and `blendWeight` reads zero entropy as "clearly on one
thing", so a brand-new session discounted the long-term profile to 40% for a user the app knew
plenty about. Empty now reads as maximum spread. Found by a selection test.

---

## Not fixed

### B16 · WEB_REMIX is unusable — P1 · WON'T FIX (no fix available)

Its media URL is refused at byte 0 with `pot` and `n` both present *and* both stripped, so what is
rejected is the signature. The live player (`e937390a`, STS 20684) has no extractable descrambler —
the classic `split("")…join("")` form is absent because the routine now runs inside the player's own
bytecode interpreter. The app pins to `edf92b2c` and the server does not honour it.

Costs nothing today because VISIONOS needs neither cipher nor proof-of-origin, but it is a single
point of failure: if VISIONOS is ever refused there is no fallback that streams whole files.

### B17 · Long items intermittently fail to start — REJECTED, not a defect · finding 49

Originally logged as a probe-offset cost. Measured against the actual 1.5 GB recitation: 0.30 s at
1%, 0.44 s at 20%, 0.14 s at 40% — a ranged request is O(1) for the CDN whatever the offset, and the
same file then reached audio in 426 ms. A second hypothesis (restore racing a user play) also failed
to reproduce. The failures correlate with the **emulator's `system_server` dying**, which happened
twice under the load of repeated force-stops. Re-open with a device log if it recurs on real
hardware.

---

## Test-infrastructure defects

Recorded because a dishonest test is more dangerous than a missing one.

### T1 · The playback oracle read a dead media session — P0 · FIXED · finding 44

`dumpsys media_session` retains entries for sessions that are gone. After a run of force-stops the
device listed seven mentions of the package — one PLAYING at 64 s and three stale `ERROR(7)` rows at
position 0 — and the oracle took the first match, reporting a **reproducible** failure while audio
was plainly playing. Now takes the most recently updated session.

### T2 · The skin capture script silently failed for every skin — P1 · FIXED · finding 48

Its route tapped the *word* "Search", which only exists on an empty Home, and `set -e` aborted each
run. So `skins-distinct` passed for hours against captures taken before the palette changed. Caught
only because a batch of ten "successes" printed ten misses.

### T3 · Two contrast oracles that could not fail — P1 · FIXED

The first `no-fixed-scrims` implementation matched to the first `)`, which lands *inside* a gradient
stop's own `copy( … )` call, so it read an almost-empty body and passed a tree that still contained
the bug. The second flagged the legacy player's conditional gradients, which do follow the theme.
Only the third is honest. Each version was checked against a negative control.
