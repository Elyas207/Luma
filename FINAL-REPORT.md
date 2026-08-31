# Luma — autonomous run, 2026-08-30/31

Branch `autonomous/2026-08-30` · 23 commits · 331 files changed (+21,830 / −3,436)
Release APK: `Luma-autonomous-2026-08-30.apk`, 18 MB, **debug-signed** (see Delivery).

Read §2 first. It is the part that matters.

> **Late fix, after the report was first written.** The owner reported that pressing **Love**
> crashed the whole app. Reproduced, traced, fixed and verified both ways on device; the
> release APK was rebuilt and the published asset replaced (hash-checked against the local
> build). Details in §1 and finding 51.

---

## 1. What was built, per track

### Playback (not in the brief, but it was broken)

Before anything else: every track stopped after 46–58 seconds. The session token was minted
once and reused indefinitely; YouTube answers an aged token the same way it answers a
forged one — *"Sign in to confirm you're not a bot"* — which knocked out the clients that
stream uncapped and forced every resolve down to IOS. An un-attested googlevideo stream
serves only the first **20%** of a file (measured: 206 up to offset 1,078,308 of a
5,365,234-byte file, 403 from 1,108,040, unchanged on retry). Minting a fresh token per
process fixed it. Verified: 4/4 tracks sustaining ~90 s, on phone and tablet, debug and
release builds.

Also fixed, all found by testing rather than reading: the first play after a fresh install
never started (a preference write recreated the activity mid-play); a logged-out install
believed it was signed in; an off-main-thread preference write silently dropped its value
and toasted an error at the user.

**Pressing Love crashed the app** (reported by the owner, finding 51). The click handler read
`player.currentMediaItem` *inside* `Database.asyncTransaction`, which runs on Room's transaction
executor. Media3 calls `verifyApplicationThread()` on every Player access and throws off the
main thread, and the throw landed in a bare executor with no handler, so the process died and
the user was dropped to the launcher. The id is now read on the calling thread and the plain
`String` passed in. Verified both directions: the previous build crashes to the launcher, the
fixed one stays foreground and the label flips LOVE → LOVED. A `no-player-in-transaction` check
now guards the pattern, because the broken version looks entirely reasonable.

### Track A — UX

Stage 0 inventory and a 50-finding audit (`docs/audit/00-inventory.md`,
`01-ux-findings.md`), then fixes in the brief's batch order.

- **Batch 1, foundations.** The audit counted **17 corner radii across 92 uses and zero
  elevation anywhere**. Added `LumaRadius`, `LumaDepth` and `LumaIntensity`; migrated 89
  radius literals (62 with no visual change at all, since the scale codifies the values
  already dominant). Added a `radius-scale` check so it cannot drift back.
- **Batch 2, navigation.** Settings, History, Statistics, Appearance, Your listening,
  Handoff and Car Mode were reachable **only** through the overflow on the search-results
  screen — you had to perform a search to reach Settings. All eight are now one tap from
  Home, via the same menu rather than a copy of it.
- **Batch 3–6, selectively.** Statistics stopped clipping titles at both ends (a 200dp
  adaptive grid on a 411dp phone); the personalisation screen stopped drawing its title
  under the status bar and its switch on top of its own description; Library's empty
  placeholders stopped being the brightest thing on screen; the mini player stopped being
  drawn under the navigation bar and stopped running two marquees at once.

### Track B — intelligence engine, P0–P5

Reconciled against the codebase first (`docs/audit/02-intelligence-plan.md`), including
where I think the architecture is over-built for what this app can actually supply.

- **P0** — append-only `listening_events` (schema 39), provenance on every row, ULIDs with
  a monotonic guard, an injectable clock. Wired into real playback and **verified on a
  device**: a track chosen from search records `search` and keeps it through its own skip,
  while the track the app moves to next is not credited to the user.
- **P1** — evidence extraction (direction and trust as separate numbers), affinity cells
  with decay and confidence shrinkage, and a replay harness driving the same classes the
  app runs.
- **P2** — suppression with three strikes on *distinct days*, 90-day self-expiry,
  forgiveness multiplier; and the controls shipped alongside it: private session, forget
  the last 24 h / 7 d / 30 d, full reset that clears the raw log too.
- **P3–P5** — session intent blending (bounded, never 1.0), circuit breakers, the context
  significance gate, and exploration with a 2% floor and per-direction cooldowns.
- **P6** — not built, as instructed.

### Theme — Frutiger Aero

Studied the references numerically rather than impressionistically
(`docs/theme/00-aero-study.md`): sampled palettes, and a vertical luminance profile of
153 / **229** / 79 showing the specular band sits **upper-middle**, not at the top. The
existing gloss recipe was "brightest at the very top, gone by the midpoint" — the modern
glassmorphism shape the brief warns against — and is now corrected, with a bevel added.

Built as the existing **Aurora** skin plus the shared token layer, not as a second theme
system (see `DECISIONS.md` D1).

### Testing

153 unit tests (from 61), a replay harness with user archetypes, and device oracles for
playback, transport, audio focus, contrast, skins and crash-freedom. Plus a manual device
script (`docs/testing/02-device-script.md`) and measured performance
(`docs/testing/04-performance.md`).

---

## 2. What was NOT finished, and why

**This is the honest part.**

### Not done at all

- **Component library and gallery** (theme Stage 2). The gloss/bevel/depth *tokens* exist
  and the skin material uses them, but button, sheet, tab bar, slider and toggle were not
  individually rebuilt against them, and there is no gallery screen. The scrubber — which
  the brief calls the most-looked-at control and the place this aesthetic earns its keep —
  is untouched.
- **No theme assets ship.** The asset library is 1.3 GB / 1284 PNGs against a 400 KB
  per-image gate. Nothing was selected, compressed or shipped, so the added bundle size is
  0 bytes and all gloss is procedural. Defensible, but it is not what the brief asked for.
- **Test tiers 4 and 5** (network/lifecycle, UI edge cases and accessibility) are not
  written. Tier 1–3 and 6 exist in some form.
- **The selection pipeline exists but is not wired into playback.** `Selection.kt`
  implements hard filters, the linear scorer, the pool guard and the diversity quota, with
  19 tests over what it *chooses* — but autoplay still uses the existing radio behaviour, so
  the engine **observes correctly and does not yet decide**. Connecting it is step 1 in §7.
  (An earlier draft of this report claimed this was already built when none of it existed.
  It does now; the claim was corrected rather than quietly left standing.)
- ~~`docs/testing/03-bugs.md` was never created.~~ **Now written** — 17 defects plus 3
  test-infrastructure defects, each with reproduction, expected, actual, cause and fix.

### Partly done

- **30 of 50 findings remain `OPEN`**, 18 fixed, 2 partly, 4 rejected as my own error. The
  open ones are mostly P2 polish, plus the queue screen (upstream chrome, six unlabelled
  icons) and the two parallel UI languages (system-level finding X2), which is a large
  refactor I judged too risky to attempt unsupervised without test coverage over playback.
- **Duplicate components not collapsed.** Five dialog base implementations and two action
  sheet systems still exist. Batch 1 asked for this; I did the token half and not the
  structural half, because merging 32 dialog files with no tests over them is exactly the
  change that breaks an app silently.

### Known to be broken / unresolved

- **Finding 49: very long items are slow to start.** A ~10-hour recitation intermittently
  fails to reach first audio within 90 s, while ordinary tracks start in 4–6 s. Diagnosed —
  the URL validation probe seeks to 40% of `clen`, which for a multi-hour file is an
  enormous offset — but not fixed, because the fix sits in the resolver hot path and a
  hasty change there risks the playback work that took the longest to get right.
- **WEB_REMIX cannot be used.** Its media URL is refused at byte 0 with `pot` and `n` both
  present and both stripped, so what is rejected is the signature; the live player has no
  extractable descrambler. Free today because VISIONOS needs neither cipher nor
  proof-of-origin, but it is a single point of failure.

---

## 3. Assumptions you need to review

Full list in `DECISIONS.md`. The ones that actually need you:

1. **The app is not an Islamic media app.** All four prompts describe Quran, nasheeds,
   lectures and du'a. The codebase is a general YouTube Music client with no content
   metadata. The architecture's central asymmetry — recitation may be reordered but never
   suppressed — therefore has nothing to key off. I implemented `content_class` as a
   first-class field defaulting to `UNKNOWN`, with the protection firing only on `QURAN`.
   **If this is meant to be an Islamic-content app, that classifier needs real metadata,
   and until it does the Quran protection protects nothing.**
2. **Prayer-relative time buckets are degraded.** They need prayer times, which need
   location, which this app does not request. I built the bucket interface and the
   significance gate (which is the part that prevents harm) and derived buckets from local
   time. Adding a location permission to a music app was not my call.
3. **The discovery pool ships disabled.** There is no curated pool and no content-safety
   layer. For an app framed this way that is a human decision, not a ranking one.
4. **Persistent queue is now on by default.** It was off, which is why nothing resumed. This
   changes behaviour for existing installs — it only adds resume, but it is a default change.
5. **Light-skin secondary text was darkened ~8%** to hold 4.5:1 under the artwork wash, and
   the atmosphere was eased from 0.75 to 0.5. Both are visible aesthetic changes made for
   legibility.

---

## 4. Known weaknesses, in my own judgement

- **The engine observes but does not decide.** The most interesting half — the ranker
  actually choosing what plays next — is implemented and tested but not connected. A reader
  of the commit log could easily overestimate what the app does today.
- **My own findings were wrong four times** (17, 33, 40, and part of 14). Each was caught by
  looking again rather than by reasoning, which suggests the audit's error rate is not zero
  and the 30 open findings deserve the same scepticism.
- **A P0 crash reached the owner before I found it.** Pressing Love — an obvious, everyday
  control — took the app down, and three refinement passes did not catch it because my sweeps
  drove *navigation* and playback rather than the secondary controls on each screen. The audit
  rubric asks whether every interaction gives feedback; I checked that controls were labelled
  and legible without ever pressing most of them. That is the single biggest process gap in
  this run.
- **Test oracles failed dishonestly twice.** One read a dead media session and reported a
  reproducible playback failure while audio was playing; one had been silently failing for
  every skin, so a check passed against stale evidence for hours. Both are fixed, but the
  lesson is that the harness needs the same suspicion as the code — and a green check is
  only as good as the last time someone proved it could go red.
- **Nothing is tested on real hardware.** Two x86_64 emulators. Frame timings, battery and
  every interruption case are unverified in the way that matters.
- **The gloss is barely visible in practice.** Car Mode is Minimal by design, and most
  surfaces sit at Reduced, so the corrected specular recipe shows up on far fewer screens
  than the theme brief intends. The aesthetic is more "calm and legible" than "2007".

---

## 5. Test results

- **153 unit tests, 0 failures**, suite run repeatedly to confirm no order dependence.
  Every new test was confirmed to fail before it passed by breaking the code deliberately.
- **Device oracles**: playback (sustained 90 s), transport, audio focus, contrast, skins
  distinct, skins hold their palette during playback, no crash logs, app alive.
- **Covered**: playback resolution and continuity, resume across process death, audio
  focus, the whole intelligence layer, contrast on every primary screen across four skins,
  theme integrity.
- **Not covered**: real interruptions, Bluetooth, network transitions, downloads offline,
  UI at maximum font size, TalkBack, and anything requiring a physical device.
- **Could not test here**: battery, mid-range frame timings, Android Auto, a real car.

## 6. Performance measured

Cold start median **1431 ms** (5 runs). Scroll: **830 frames, 0 janky**, 99th percentile
21 ms — 2 ms off the median. Memory **236 MB PSS**. Release APK **18 MB**. Time to first
audio **4.4–5.5 s**.

All on an emulator. The mid-range 60 fps gate is **untested**, not passed.

## 7. What I would do next, in order

1. Wire `Selection.kt` into autoplay. Everything it needs already exists and is tested.
2. Cache the last working InnerTube client. Every resolve currently retries WEB_REMIX
   first and fails, which is most of the 4–6 s to first audio.
4. Collapse the five dialogs and two action sheets — with tests written first.
5. The component library and the scrubber, which is where this theme would actually show.
6. Run `docs/testing/02-device-script.md` on a real phone in a real car.

## 8. Delivery

- Branch `autonomous/2026-08-30` pushed to `github.com/Elyas207/Luma`, verified against
  the remote (`712606bc2`, 0 unpushed).
- **The PR does not target `main`, and that needs your decision.** `Luma` is not a rename
  of the fork: its `main` is a single "Initial commit" sharing no history with this
  codebase, so GitHub refuses a PR against it and force-pushing the fork's history onto
  `main` is forbidden by the brief. I pushed the unmodified starting commit
  (`f02577e86`) as `baseline/pre-autonomous-2026-08-30` and opened
  [PR #1](https://github.com/Elyas207/Luma/pull/1) against that, so the PR shows exactly
  this run's diff. Deciding what `main` should contain is yours.
- Release `autonomous-2026-08-30` created with the APK attached (18,216,496 bytes,
  `state=uploaded`, verified by querying the release rather than trusting the upload).
- **The APK is debug-signed.** No release keystore exists in this checkout and creating a
  signing identity is not a decision I should make unprompted. It installs and plays, but
  it cannot be shipped to users or upgraded over a real release build.
- No keystores, `.env` files, tokens or credentials were committed; `.claude/`, `.unlazy/`
  and `.agents/` are git-ignored. The diff was checked before each push.
