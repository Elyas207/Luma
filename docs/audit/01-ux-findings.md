# Stage 1A — UX findings

Audited 2026-08-30 against `61e241abe`, on emulator-5556 (phone 1080x2400) under the
**Aurora** skin, with a populated library and history. Screenshots in
`scratchpad/ux/phone/`. Each finding has a status kept current through Stage 2.

Status key: `OPEN` · `FIXED` · `DEFERRED` (with reason) · `REJECTED` (with reason).

---

## S1. Home (`themed/now/NowScreen.kt`)

**Purpose.** Get the returning user back into what they were listening to.
**Primary action.** Resume the hero item.

1. **Primary navigation sits below the carousel.** `Your library`, `Search` and `Car mode`
   render at the *bottom* of Home, below the "Back to" row — **4 full swipes** on a
   1080x2400 phone. Above the fold the only affordances are the hero Play and a search
   icon. A returning user has no visible route to their own library.
   *Why it's wrong:* the app's three top-level destinations are effectively hidden the
   moment the user has any history, which is permanently after day one.
   **P0.** *Fix:* persistent top-level navigation that does not depend on scroll position.
   — `OPEN`
2. **The empty-state Home and the populated Home have different navigation.** On first run
   the three links are visible near the top; once content exists they move below it. The
   information architecture changes shape under the user.
   **P1.** *Fix:* same nav in both states. — `OPEN`
3. **The search affordance is icon-only with no visible label on populated Home,** while
   the empty state renders the word "Search". Inconsistent, and the icon is the only route
   to search from Home.
   **P2.** *Fix:* one treatment for both states. — `OPEN`

## S2. Search (`themed/luma/LumaSearchScreen.kt`)

**Purpose.** Find something by name. **Primary action.** Submit the query.

4. **No back affordance on screen.** Relies entirely on system back / gesture.
   **P1.** *Fix:* a back control consistent with the rest of the app. — `OPEN`

## S3. Search results (`ui/screens/searchresult/SearchResultScreen.kt`)

**Purpose.** Pick from matches. **Primary action.** Play a result.

5. **This screen is the app's only route to Settings, History, Statistics, Appearance,
   Your listening, Handoff and Car Mode** — all of them live behind its `More` overflow.
   A user who wants Settings must first perform a search.
   *Why it's wrong:* it is not a navigation surface, and nothing about it suggests it
   holds the app's entire secondary IA.
   **P0.** *Fix:* move these to a real top-level destination; keep contextual actions
   (sort, filter) in the results overflow. — `FIXED` (da69… → Home masthead now renders the
   same `HamburgerMenu`; all 8 destinations are 1 tap from Home, verified on device)
6. Every row carries a download icon, competing with the row's own primary action (play).
   **P2.** *Fix:* move download into the row's overflow. — `OPEN`

## S4. Player (`themed/player/LumaPlayer.kt`)

**Purpose.** Control what is playing. **Primary action.** Play/pause.

7. **Secondary transport (prev/next) renders mid-grey on a light surface.** Measured on
   the Aurora capture, the glyphs are far lower contrast than the play control.
   **P1.** *Fix:* transport glyphs use `LumaColor.Ink`, not a fixed grey. — `OPEN`
8. Progress arc and play button take the artwork accent, which on some artwork lands close
   to the skin accent and on others clashes with it. There is no rule keeping the two apart.
   **P2.** *Fix:* accent selection respects the skin's accent as a floor. — `OPEN`

## S5. Queue

**Purpose.** See and reorder what is next. **Primary action.** Reorder / jump.

9. **The now-playing row truncates from the left** on mixed-script content: the artist
   renders as `ashary Rashed El Afasi - راشد العفاسى` with the leading characters cut.
   Every other row truncates from the right.
   **P1.** *Fix:* single truncation rule; do not centre-align mixed-script rows. — `OPEN`
10. **Two stray blue dots are drawn over the now-playing thumbnail,** landing on the
    reciter's face. Appears to be a drag-handle or selection artifact rendered in the wrong
    place.
    **P1.** — `FIXED`. Traced: `NowPlayingSongIndicator` defaults its size to
    `Dimensions.thumbnails.song`, the *list-row* thumbnail, wherever it is placed — so over the
    mini player's 44dp disc the equaliser animation spilled outside the artwork. Sizing it
    correctly stopped the spill, but the real answer was that the mini player *is* the playing
    item, so a "now playing" badge on its own artwork is redundant and competes with the one
    image on the control. Removed there; kept in list rows where it distinguishes a row.
11. **The mini-player repeats the queue's own now-playing row** directly beneath it — the
    same title, artist and artwork twice on one screen.
    **P1.** *Fix:* suppress the mini-player on the queue surface. — `OPEN`
12. **The bottom toolbar is six unlabelled icons** (count, crosshair, magnifier, padlock,
    ellipsis, chevron). "Crosshair" and "padlock" are not guessable.
    *Why it's wrong:* fails "icons legible without guessing"; a padlock in a music app
    reads as security, not as queue-lock.
    **P1.** *Fix:* label them or reduce to the two that earn their place. — `OPEN`
13. This whole surface is upstream chrome, not the Luma language — different toolbar,
    different type, different spacing from every screen around it.
    **P1 (system-level, see X2).** — `OPEN`

## S6. Library (`themed/library/LibraryScreen.kt`)

**Purpose.** Find something already saved. **Primary action.** Open a saved item.

14. **Artist and album tiles are giant empty placeholders.** Artists render as ~230px
    white circles containing a single letter; albums as ~290px white squares containing a
    single letter. Two full rows of near-empty shapes occupy the top two-thirds of the
    screen, pushing the only rows with real artwork (Songs) below the fold.
    *Why it's wrong:* the eye is drawn to the largest, emptiest elements, and the actual
    content is what gets cut off.
    **P1.** *Fix:* use real artwork where it exists; shrink the placeholder scale
    substantially; lead with content that has imagery. — `PARTLY FIXED` (re-diagnosed: the
    sizes are reasonable at 104/144dp — the defect is that an *empty* slot filled with
    `LumaColor.Raised`, which is lighter than the page on light skins, so the emptiest
    elements were the brightest. Placeholders now sit at 0.7 alpha with a smaller monogram.
    A first attempt at 0.45 alpha on `InkFaint` measured 2.36:1 and was caught by the
    contrast check; the shipped version measures 5.46:1. Using real artist imagery where
    the API provides it is still open.)
15. **No back affordance.** **P1.** — `OPEN`

## S7. Car Mode (`themed/car/CarModeScreen.kt`)

Audited as a driver: glance-length looks, arm's length, direct sun.

16. **A photographic sky background sits behind the entire surface**, including behind the
    text. This is a direct breach of the theme prompt's Car Mode hard gate ("no
    photographic backgrounds") and of the legibility rule ("text never sits directly on a
    photograph").
    **P0.** *Fix:* Car Mode gets a flat, high-contrast surface; the skin may set its
    colour and nothing else. — `FIXED` (Car Mode declares `LumaIntensity.Minimal`, and
    `SkinOrnamentLayer` now honours intensity, so this holds for every skin rather than
    only the restrained ones. Verified on Aurora: the sky is gone from Car Mode and still
    present on Home.)
17. ~~**Prev and next glyphs are mid-grey on white.**~~ **REJECTED — I was wrong.** Re-read
    against `CarTransport.kt`: enabled glyphs already draw at `LumaColor.Ink` full alpha;
    the grey ones in the capture were the *disabled* state (no previous track, empty
    queue) at 0.3 alpha. That is honest state, not a contrast defect. Superseded by 17b.
17b. **Disabled transport is too faint to read as "present but unavailable" in a car** at
    0.3 alpha. **P2.** *Fix:* raise disabled alpha under `Minimal` intensity. — `OPEN`
18. **Transport sits at the vertical middle** in portrait. In a phone cradle the natural
    thumb arc is the lower third.
    **P1.** *Fix:* anchor transport to the lower third in portrait. — `OPEN`
19. **An empty progress bar and `0:00 / 0:00` occupy prime real estate when nothing is
    playing,** and the artwork slot is a large blank plate.
    **P2.** *Fix:* collapse the transport furniture in the idle state. — `OPEN`
20. No route back to Home from Car Mode other than a chevron that reads as "collapse".
    **P2.** — `OPEN`

## S8. Settings (`ui/screens/settings/SettingsScreen.kt`)

21. **Two search affordances on one screen**: the circular search in the header and a
    second, unlabelled cyan magnifier immediately below it.
    **P1.** *Fix:* one. — `OPEN`
22. **`Check now` renders as pale text on a pale pill** — the lowest-contrast interactive
    element found in the audit.
    **P1 (contrast).** — `OPEN`
23. Section labels (`UPDATE`, `LANGUAGES`, `PLAYER`) are small grey caps on a pale ground;
    likely below 4.5:1 on light skins. Needs measurement, then a token that guarantees it.
    **P1.** — `OPEN`
24. `Currently selected: System language` sits between the section label and its divider
    in a third, lighter grey — a fourth text colour in one header block.
    **P2.** — `OPEN`

## S9. History (`ui/screens/history/HistoryScreen.kt`)

25. ~250px of dead space between the header controls and the `History` title.
    **P2.** — `OPEN`
26. The `Today` group header is a full-width white card ~120px tall containing one word.
    Disproportionate to its job.
    **P2.** — `OPEN`
27. `YTM History` is jargon. **P2.** *Fix:* "YouTube Music history". — `OPEN`

## S10. Statistics (`ui/screens/statistics/StatisticsScreen.kt`)

28. **Titles are clipped on both sides in the two-column grid.** Observed: `Allah Humm`,
    `Hotel (feat. R.Ke`, `-Z, Boo & Gotti)`, `Kun Fayak`, `lmer Z`, `dy  Ca`, `liohea`,
    `ex Wa`. Text overflows its cell rather than truncating cleanly, and some strings are
    cut at the *start*.
    *Why it's wrong:* it looks broken, and on a phone a two-column grid cannot hold a real
    track title at this type size.
    **P0.** *Fix:* single column on phone; one truncation rule; `Ellipsis` at the end only.
    — `FIXED` (adaptive minimum raised 200dp → 320dp, so a 411dp phone gets one column and
    a tablet still gets several; verified on device, titles render in full)
29. **`1h 23m 48.168s time spent`** — millisecond precision in a human-facing total.
    **P2.** *Fix:* round to seconds, drop the fraction. — `FIXED` (now "1h 23m 48s")
30. Rank numerals are drawn over the artwork bottom-left, colliding with image content.
    **P2.** — `OPEN`

## S11. Your listening (`themed/taste/TasteCentreScreen.kt`)

31. **The title collides with the status bar.** `Your listening` renders at the very top of
    the window with no top inset, overlapping the system clock row.
    **P0 (visual breakage).** *Fix:* apply status-bar insets. — `FIXED` (statusBarsPadding; verified on device)
32. **The toggle overlaps its own description.** The switch is drawn on top of the wrapping
    description text ("…to order what **gets suggested**"), obscuring words.
    **P0.** *Fix:* constrain the text column; the control gets its own gutter. — `FIXED` (16dp gutter; description now wraps clear of the switch)
33. ~~**`Reset everything the app has learned` is an unprotected destructive action.**~~
    **REJECTED — I was wrong.** Reading the code, it is already a two-tap confirm that relabels to
    "Tap again to forget everything" and switches to the alarm colour. A modal would be heavier
    than a reversible preference deserves. Superseded by 33b.
33b. **The reset only cleared the derived counters, not the raw event log**, so "forget everything"
    left the evidence behind. **P1.** — `FIXED` (clears both).
33c. **A settings row's label is not tappable — only the switch is.** Found while testing the
    private session: tapping the words did nothing. A ~50x30dp target at the far edge of a 411dp
    row, for the row's primary action. **P1.** — `FIXED` (the whole row toggles).
34. **The surface is missing most of what the architecture requires of it** (§12): no
    "Reduced" list of suppressed items, no per-facet reset, no "forget the last 24h/7d/30d",
    no private session, no pause-learning, no per-line undo/why. It exposes a single global
    switch and a nuclear reset.
    **P1.** *Fix:* build out as part of Stage 3 P2, which is where it belongs. — `PARTLY FIXED`
    (private session, and forget-the-last-24h/7d/30d, both shipped with the inference as the
    architecture requires. Per-facet reset and the per-line "why / not accurate" controls are still
    open.)
35. ~60% of the screen is empty below the reset button.
    **P2.** — `OPEN`

## S12. Handoff, Appearance, Profiles

36. All three are reachable only through the search-results overflow (see finding 5).
    **P0, tracked by 5.** — `OPEN`
37. Appearance is the only screen in the app with a genuinely strong first impression
    (ten named skins with taglines). It is buried four taps deep.
    **P1.** *Fix:* surface it from Settings and from the top-level nav. — `OPEN`

---

## Phone

38. One-handed reach: the primary navigation is at the extreme bottom of a long scroll
    (finding 1) while the search icon is in the top-right corner — the two most-used
    controls are at opposite, hard-to-reach ends.
    **P1.** — `OPEN`
39. Two-column grids (Statistics) are wrong at this width with real content (finding 28).
    **P1.** — `OPEN`

## Tablet

40. ~~**The tablet runs the phone layout scaled up.**~~ **REJECTED — I was wrong.** Looking
    at the tablet properly in pass 2, Home is a genuine two-pane layout: hero on the left,
    the "Back to" grid on the right, with `WideNow` selected at ≥720dp. My Stage 1 sweep must
    have captured a narrow window. What the tablet *did* have was a contrast failure the phone
    did not — see 46.

## Car Mode

Findings 16–20 above. Additionally:

41. Car Mode inherits the skin's ornament wholesale, so a skin with a photographic
    backdrop puts photography behind driving controls (finding 16 is the instance; this is
    the mechanism). Car Mode must opt out of ornament at the system level, not per skin.
    **P0 (system-level, see X4).** — `FIXED` (intensity is now a surface property;
    `SkinOrnamentLayer` draws nothing at Minimal and downgrades photography to a gradient
    wash at Reduced.)

---

## Top 10 problems, ranked by daily-user harm

1. **(5, 1)** The app's entire secondary IA lives behind a search-results overflow, and its
   top-level links are below a carousel. Everything else on this list is smaller than this.
2. **(16, 17)** Car Mode puts photography behind text and renders its secondary transport
   in low-contrast grey — the one surface where a mistake has a physical cost.
3. **(28)** Statistics clips titles on both sides; the screen reads as broken.
4. **(31, 32)** "Your listening" overlaps the status bar and draws its switch on top of its
   own text — the surface whose entire job is trust.
5. **(14)** Library leads with two rows of giant empty placeholders and pushes real content
   off-screen.
6. **(9, 10, 11, 12)** The queue: left-truncated mixed-script rows, stray dots over
   artwork, a duplicated now-playing row, and six unlabelled icons.
7. **(40)** The tablet is a scaled phone.
8. **(21, 22, 23)** Settings: duplicate search, a near-invisible primary button, and
   unmeasured label contrast.
9. **(34)** The personalisation surface promises transparency and provides a single switch.
10. **(7)** Player secondary transport contrast.

## System-level problems — these get fixed first

- **X1. There is no radius or elevation scale.** 17 distinct corner radii across 92 uses,
  and **zero** elevation/shadow usage anywhere in 589 files. Colour and type are already
  centralised; these two are not. Every card, sheet and button therefore has a hand-picked
  corner and no depth, which is also why the app cannot currently express the Aero
  aesthetic — gloss, bevel and shadow have nothing to hang on.
- **X2. Two parallel UI languages ship simultaneously.** The Luma surfaces
  (`themed/**`) and the inherited upstream surfaces (`ui/screens/**`) have different
  headers, toolbars, type and spacing, and the user crosses between them constantly
  (player → queue, home → statistics).
- **X3. Duplicate components.** Five dialog base implementations, two live action-sheet
  systems, two players. Any fix has to be made in two or five places or it drifts.
- **X4. Ornament is applied globally rather than per surface.** A skin's backdrop reaches
  Car Mode and reaches behind body text, which is what makes the light skins fragile.
  Intensity (Full / Reduced / Minimal) needs to be a property of the surface.
- **X5. Contrast is unmeasured.** No screen has a recorded worst-case ratio. Several
  candidates found by eye (22, 23, 17). Needs a measurement tool, not opinions.
  — `FIXED` (`tools/unlazy-checks/contrast.mjs` reads text-node bounds from uiautomator and
  samples the real framebuffer, reporting the WCAG ratio between the darkest and lightest
  pixel actually present behind each text region — the extremes, as the theme brief requires,
  not the average. It immediately found a genuine failure: the greeting label at **2.90:1**
  against the 3:1 large-text requirement, because `LumaLabel` defaulted to `InkFaint`, which
  is the *disabled* role and is allowed to be low contrast precisely because nobody needs to
  read it. Now `InkSoft`. Home measured on four skins spanning light and dark: Aurora 4.08:1,
  Terrazzo 5.02:1, Obsidian 5.23:1, Ember 6.74:1 — all pass, with the mean luma of each
  capture confirming the skin actually changed.)

## Batch order for Stage 2, and why

1. **Batch 1 — foundations**: radius + elevation scales, surface-intensity token, then
   collapse dialogs and action sheets onto one implementation each. Chosen first because
   X1 blocks the entire theme track and X3 doubles the cost of every later fix.
2. **Batch 2 — P0 navigation**: findings 1, 5, 31, 32. The IA problem is the single
   largest source of daily friction and everything else is cosmetic beside it.
3. **Batch 3 — hierarchy and depth**: 14, 28, 9–13, 21–24.
4. **Batch 4 — Car Mode**: 16–20, 41. Treated as its own surface with its own intensity.
5. **Batch 5 — Tablet**: 40.
6. **Batch 6 — polish**: the P2 tail.


---

## Refinement pass 1 — new findings

Found by walking the app again after the Stage 2/3 work, on a dark skin.

42. **The mini player was drawn underneath the system navigation bar**, so the back, home and
    recents glyphs sat on top of the artist name. Nothing else on that surface reserves room for
    the system bars, and the mini player floats above the content.
    **P1.** — `FIXED` (`navigationBarsPadding()`).
43. **Both mini-player lines ran an independent marquee**, so at any given instant it read as
    garbled — the title showing its tail directly above the artist showing its middle. Two things
    moving in a control that size is noise, not information.
    **P1.** — `FIXED` (marquee on the title only; the artist truncates. Also one fewer animation
    running during playback).
44. **The playback oracle read a dead media session.** `dumpsys media_session` retains entries for
    sessions that are gone; after a run of force-stops the device listed seven mentions of the
    package, one PLAYING at 64s and three stale `ERROR(7)` rows at position zero. The oracle took
    the first match and reported a hard, *reproducible* failure while audio was plainly playing.
    **P0 (test infrastructure).** — `FIXED` (takes the most recently updated session). Worth
    recording as a finding in its own right: a test that fails consistently for the wrong reason is
    more dangerous than one that fails intermittently, because it gets believed.
45. The emulator's `system_server` died under the load of repeated force-stops mid-pass, which
    presented as a series of playback failures. Environmental, not an app defect — noted so the
    same symptom is not misread next time.

---

## Refinement pass 2 — new findings

Deliberately spent on the surfaces I had looked at least, starting with the tablet.

46. **Text over hero artwork failed contrast on the tablet, badly.** "PICK UP WHERE YOU LEFT
    OFF" measured **1.97:1** over a bright sunset, against the 4.5:1 an 11sp label needs. The
    phone passed because its hero is shorter, so the label sits lower in the scrim; on a tablet
    the hero is tall enough that the text block lands near the middle of the image.
    *Why the first fix was not enough:* strengthening the gradient reached only 2.99:1. A
    gradient cannot carry small text over *arbitrary* artwork, because the hero shows whatever
    was last played and those pixels are unknowable at design time.
    **P0.** — `FIXED`. The hero's text block now sits on a near-solid plate, which is the theme
    brief's own rule: if a design needs text over an image, the design changes. Arch-tile
    captions got the same treatment after measuring 3.68:1.
47. **Secondary text had no headroom under the artwork wash.** `InkSoft` cleared 4.5:1 against
    a plain ground on every light skin (4.64–5.09) but the artwork atmosphere costs roughly half
    a point, which put the greeting at 4.02:1.
    **P1.** — `FIXED` (atmosphere eased 0.75 → 0.5, and the four light skins' secondary darkened
    ~8% to 5.7–6.9:1 on plain ground). Decoration loses to legibility.

**Result:** tablet home worst case 1.97:1 → **5.22:1**; phone home → **6.50:1**. Both pass.

---

## Refinement pass 3 — findings

48. **The skin capture script had been failing silently for every skin**, so `skins-distinct` was
    passing against stale evidence. Its route tapped the *word* "Search", which only exists on an
    empty Home; on a populated one it is an icon, and `set -e` aborted each run. Caught because a
    batch of ten "successes" printed ten misses.
    **P1 (evidence integrity).** — `FIXED` (routes via the destinations menu, which is now one tap
    from Home). All ten skins re-captured; still distinct.
49. **Very long items are slow to reach first audio.** A ~10-hour recitation intermittently failed
    to start within the oracle's window while three-to-five minute tracks started reliably in
    4-6 s. The deep URL validation probes at 40% of `clen`, which for a multi-hour file is an
    enormous byte offset, and the first ranged fetch follows it.
    **P1.** — `OPEN`, and honestly diagnosed rather than fixed: the probe offset should be capped
    in absolute bytes rather than scaled with file length. Recorded in the final report as
    outstanding.
50. The oracle's settle window (45 s) was tuned before playback state was restored at launch. A
    cold start now restores the queue *and* performs a full client sweep for whatever is tapped.
    **P2 (harness).** — `FIXED` (90 s; polling costs nothing when the answer comes early).

---

## Reported by the owner during the run

51. **The app crashes when you press "Love".** Reported from a real phone, reproduced on the
    emulator, and traced from the stack:

    ```
    java.lang.IllegalStateException: Player is accessed on the wrong thread.
      at androidx.media3.exoplayer.ExoPlayerImpl.verifyApplicationThread
      at app.kreate.android.service.player.StatefulPlayerImpl.getCurrentMediaItem
      at app.kreate.android.themed.player.LumaPlayerKt.SecondaryRow  (LumaPlayer.kt:514)
    ```

    `Database.asyncTransaction` runs its block on Room's transaction executor, and the handler
    read `player.currentMediaItem` *inside* that block. Media3 calls `verifyApplicationThread()`
    on every Player access and throws off the main thread; the throw landed in a bare executor
    with no handler, so the process died and the user was dropped to the launcher.

    **P0.** — `FIXED`. The id is read on the calling thread and the plain `String` handed to the
    transaction. Verified both ways on device: the previous build crashes and leaves the
    launcher in front, the fixed build stays in the foreground and the label changes LOVE →
    LOVED.

    Also added `verify.mjs no-player-in-transaction`, because the broken version looks entirely
    reasonable and the same mistake is easy to repeat. The notification path was checked and is
    already correct — `PlaybackController.getIconId` marshals every player read to
    `Dispatchers.Main`.
