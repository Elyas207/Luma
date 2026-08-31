# Gates: Luma final audit (luma-final-prompt.md)

OWNS: composeApp/src/**, modules/metrolist/innertube/src/**, tools/unlazy-checks/**, GATES.md, PLAN.md, DESIGN.md

Scope: Deliver the audit requested in `luma-final-prompt.md`, with playback as the stated priority —
find and fix the actual root cause rather than patching symptoms, and prove each outcome instead of
reporting it.

Evidence below is the observed result of running each `CHECK:` on this machine (phone =
emulator-5556 at 1080x2400, tablet = emulator-5554 at 2560x1600). Device gates need both emulators
running and Luma installed.

- [x] G1: The build is green.
  CHECK: sh gradlew assembleGithubUniversalProdDebug
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: exit=0, "BUILD SUCCESSFUL".

- [x] G2: The unit suite is green.
  CHECK: sh gradlew :composeApp:testGithubUniversalProdDebugUnitTest
  EXPECT: BUILD SUCCESSFUL
  EVIDENCE: exit=0, 61 tests, 0 failures. Includes the rewritten `ErrorHandlingPolicyTest`, which
    now pins the deliberate change that a 403 must *not* burn the fallback (see G12).

- [x] G3: Luma's colour roles derive from the active skin rather than fixed literals, and the type
      scale cannot freeze one theme.
  CHECK: node tools/unlazy-checks/verify.mjs theme-derived
  EXPECT: THEME_ROLES_DERIVED_OK
  EVIDENCE: exit=0, THEME_ROLES_DERIVED_OK.

- [x] G4: The active palette is actually pushed into those roles at the appearance provider.
  CHECK: node tools/unlazy-checks/verify.mjs theme-wired
  EXPECT: THEME_SYNC_WIRED_OK
  EVIDENCE: exit=0, THEME_SYNC_WIRED_OK.

- [x] G5: No screen pins a surface or text colour to a hardcoded ARGB literal.
  CHECK: node tools/unlazy-checks/verify.mjs no-hardcoded-surfaces
  EXPECT: NO_HARDCODED_SURFACES_OK
  EVIDENCE: exit=0, NO_HARDCODED_SURFACES_OK.

- [x] G6: Switching skin visibly repaints the app — measured from decoded framebuffers of the same
      screen under two palettes, not from the setting having been written.
  CHECK: node tools/unlazy-checks/verify.mjs device-theme-repaint emulator-5556 .unlazy/shots/theme-dark.raw .unlazy/shots/theme-light.raw
  EXPECT: DEVICE_THEME_REPAINT_OK
  EVIDENCE: exit=0. Re-captured after the playback work: Obsidian mean luma 34.1, Terrazzo 223.9,
    delta 189.8.

- [x] G7: No interactive control ships without an accessibility label.
  CHECK: node tools/unlazy-checks/verify.mjs a11y-labels
  EXPECT: A11Y_LABELS_OK
  EVIDENCE: exit=0, A11Y_LABELS_OK.

- [x] G8: Neither emulator has produced a crash log after a scripted journey across the app.
  CHECK: node tools/unlazy-checks/verify.mjs device-no-crash emulator-5554 emulator-5556
  EXPECT: DEVICE_NO_CRASH_OK
  EVIDENCE: exit=0, DEVICE_NO_CRASH_OK, after the full navigation sweep on both devices.

- [x] G9: Luma is alive and foregrounded on both the tablet and the phone after that journey.
  CHECK: node tools/unlazy-checks/verify.mjs device-app-alive emulator-5554 emulator-5556
  EXPECT: DEVICE_APP_ALIVE_OK
  EVIDENCE: exit=0, DEVICE_APP_ALIVE_OK.

- [x] G10: Every primary and secondary destination opens and renders content rather than a blank or
      broken screen, on both devices.
  EVIDENCE: `scratchpad/navsweep.sh` walked History, Statistics, Profiles, Appearance, Your
    listening, Continue on another device and Settings on both emulators after the playback
    changes. Phone: items 2-17, zero crash logs. Tablet: items 2-47, zero crash logs. Home, Search,
    results, Player, Car Mode and the Appearance list additionally checked by screenshot. Back
    navigation from Search returns to Home and then leaves the app (checked by keyevent, reading
    the resumed activity each press). No dead end found.

- [x] G11: Loading, empty and error states are intentional on every screen that can be empty.
  EVIDENCE: The earlier sweep found one dead blank pane — History rendered a title and two tabs and
    nothing else on a fresh install — fixed by adding an EmptyState to HistoryList and re-checked
    here (History now renders content). Car Mode's empty state reads "Nothing playing" / "Nothing
    queued" rather than a blank three-zone layout; Home first-run reads "Nothing playing yet.";
    "Continue on another device" and Statistics were re-checked in the sweep above.

- [x] G12: Playback starts and keeps playing a whole track, and the reason it previously did not is
      fixed at the root rather than worked around. (This gate previously recorded the honest
      failure "PLAYBACK DOES NOT WORK END TO END"; it now records the fix.)
  CHECK: node tools/unlazy-checks/playback.mjs sustained emulator-5556 "hotel california eagles"
  EXPECT: PLAYBACK_SUSTAINED_OK
  EVIDENCE: exit=0. Root cause: the session's visitor token was minted once and then reused for
    ever, so it aged out; YouTube answers an aged token exactly like a forged one ("Sign in to
    confirm you're not a bot"), which knocked out VISIONOS/ANDROID_VR/TVHTML5 and forced the sweep
    down to IOS. An IOS url is un-attested, and an un-attested googlevideo stream serves only a
    fixed *fraction* of the file: measured on a live url with clen=5365234, 2 KB ranges up to
    offset 1078308 returned 206 and everything from 1108040 on returned 403 — a 20.1% boundary that
    did not move on retry, which is exactly the 46-58s every track died at. Proven independently:
    the same VISIONOS player request that was refused with a token taken from an old log returned
    OK with 23 formats when given a freshly minted one, and session cookies made no difference
    either way. Fix is `ensureFreshVisitorToken()` — one mint per process.
    Measured after the fix: phone 4/4 distinct tracks sustained ~90s each (Bohemian Rhapsody 88.7s,
    Shape of You 92.4s, Hotel California 89.3s, Billie Jean 88.9s); tablet sustained 88.9s; client
    is VISIONOS and the transport reached offset 4,718,592 on a 4.7 MB track, i.e. far past the
    ~1 MB an un-attested stream is allowed.

- [x] G13: The app remains usable at reduced window size, including Car Mode, for Maps/Waze
      split-screen coexistence.
  EVIDENCE: emulator-5554 driven at 1280x1600 and 800x1600. Home and Car Mode both rendered full
    content at each size with zero crash logs; Car Mode abandoned its three-zone landscape split
    for a stacked layout rather than squeezing columns, transport controls stayed full size and
    nothing clipped. Re-checked at full size here: Car Mode renders artwork / now-playing / up-next
    zones with oversized transport targets (scratchpad/carmode5.png). Display reset afterwards.

- [x] G14: Pause holds the playhead and resume moves it again, driven through the media session
      rather than the app's own UI.
  CHECK: node tools/unlazy-checks/playback.mjs transport emulator-5556 "clocks coldplay"
  EXPECT: PLAYBACK_TRANSPORT_OK
  EVIDENCE: exit=0 — "pause holds the playhead", "resume advances the playhead". (The oracle
    previously dispatched via `media`, which does not exist on this system image; it now uses
    `cmd media_session dispatch`.)

- [x] G15: Luma yields audio when something else takes focus and comes back on its own — the
      Maps/Waze-in-the-car requirement.
  CHECK: node tools/unlazy-checks/playback.mjs focus emulator-5556 "clocks coldplay"
  EXPECT: PLAYBACK_AUDIOFOCUS_OK
  EVIDENCE: exit=0. An incoming call was generated with `adb emu gsm call`; `dumpsys audio` shows
    Luma's focus entry going to "loss: LOSS_TRANSIENT" under telecom's GAIN_TRANSIENT. Judged by
    the playhead rather than the state flag: 0ms drift during the call, then resumed and advancing
    (30020 -> 36050). Config backing this: USAGE_MEDIA/CONTENT_TYPE_MUSIC so navigation guidance
    ducks rather than stops it, setHandleAudioBecomingNoisy(true) for Bluetooth dropout, and
    WAKE_MODE_NETWORK for screen-off.

- [x] G16: All ten skins repaint the app and no two render alike.
  CHECK: node tools/unlazy-checks/verify.mjs skins-distinct .unlazy/shots/skins
  EXPECT: ALL_SKINS_DISTINCT_OK
  EVIDENCE: exit=0, "ALL_SKINS_DISTINCT_OK (10 skins)". Each skin was selected through the real
    Appearance screen and the same home screen captured under it (scratchpad/skin.sh). Mean luma:
    Aurora 219.5, Obsidian 34.1, Ember 43.2, Vinyl 213.1, Cassette 31.8, Terrazzo 223.9,
    Nocturne 39.7, Bloom 228.2, Graphite 45.6, Zellige 48.8; channel means separate the ones that
    share a brightness (e.g. Cassette rgb 52,24,56 vs Obsidian 34,34,34). Negative control: copying
    Obsidian's capture over Nocturne's makes the check exit 1 with "Obsidian vs Nocturne".

- [x] G17: A first play on a genuinely fresh install works, not just on an app that has run before.
  EVIDENCE: `pm clear` then grant POST_NOTIFICATIONS then play, twice, on emulator-5556: 2/2
    sustained (Creep 87.5s, Yellow 88.6s) with 0 activity recreations. This was failing 0/2 before
    the fix and is worth keeping as a distinct gate because every warm run passed while it failed —
    on a fresh install the four restart-triggering preferences are written for the first time, and
    SharedPreferences notifies on a *write* even when the stored value is the one already in use,
    so the activity recreated mid-play, stopped the player and unbound the service. MainActivity
    now compares against the value the activity was actually built with (an unset key counts as its
    default) and restarts only on a real change.

- [x] G18: A chosen skin keeps its own palette while a track is playing.
  CHECK: node tools/unlazy-checks/verify.mjs skins-hold .unlazy/shots/skins .unlazy/shots/skins-playing
  EXPECT: SKINS_HOLD_WHILE_PLAYING_OK
  EVIDENCE: exit=0 over six skins captured on emulator-5556 with playback confirmed PLAYING(3) at
    each capture (scratchpad/skin_playing.sh switches skin without restarting the app, since
    force-stopping would end playback and hide the bug). Light skins stayed light and dark skins
    stayed dark: Aurora 186.8, Terrazzo 193.6, Vinyl 184.5, Bloom 195.6, Obsidian 36.7,
    Zellige 54.3.
    This was reported by the user as "the colours are very hard to see in the frutiger aero mode".
    `COLOR_PALETTE` defaults to Dynamic (artwork-derived) and choosing a skin never cleared it, so
    as soon as audio started the artwork replaced the skin's palette while the skin carried on
    drawing its own backdrop — on Aurora, a dark palette's pale text over a bright photographic
    sky. `setDynamicPalette` and the preference-listener recompute now both defer to a selected
    skin, matching the rule the initial palette build already followed. Before the fix Aurora
    measured 39.7 while playing against 219.5 idle. Negative control: substituting Obsidian's
    capture for Aurora's makes the check exit 1.

- [x] G19: No text scrim is pinned to a fixed black or white.
  CHECK: node tools/unlazy-checks/verify.mjs no-fixed-scrims
  EXPECT: NO_FIXED_SCRIMS_OK
  EVIDENCE: exit=0. A scrim sits behind text drawn in `LumaColor.Ink`, which flips with the skin,
    so a scrim fixed to black is right on half the skins and unreadable on the other half — the
    home hero faded to 90% black and put dark navy text on a near-black base under every light
    skin. Both it and the arch-tile caption now fade to `LumaColor.Ground`. The check ignores
    `themed/skin/`, where Color.White/Black are gloss and shadow rather than backing for text, and
    ignores gradients that already branch on `lightTheme`/`ColorPaletteMode` (the legacy player
    does this) since those do follow the theme. Two earlier versions of this oracle were wrong and
    were caught by the control rather than shipped: the first matched to the first ')', which lands
    inside a stop's own `copy( … )` call and read an almost empty body, and the second flagged the
    conditional legacy gradients as defects. Negative control: restoring the black scrim in
    NowScreen makes the check exit 1.

<!--
G10, G11, G13 and G17 are manual because no command I can honestly write decides them: they need a
human-equivalent look at rendered screens, or a destructive `pm clear` that would wreck a shared
device run. Each records the exact device, route and artefact rather than an assertion.

Not fixed, recorded honestly rather than hidden:
  * WEB_REMIX still cannot be used. Its media url is refused at byte 0 with `pot` and `n` stripped
    as well as present, so the rejection is the signature, and the live player (e937390a, STS
    20684) no longer contains an extractable descrambler — the classic `split("")…join("")` form is
    absent and the transform-object shape returns zero matches, because the routine now runs inside
    the player's own bytecode interpreter. The app pins to player edf92b2c (STS 20629) instead.
    This costs nothing today: VISIONOS needs no cipher and no proof-of-origin and streams whole
    files. Its client stanza was refreshed in passing (0.1 -> 1.02); that was not the fix — with a
    fresh visitor token the old stanza is answered OK as well, it is simply offered fewer formats
    (17 vs 23).
Fixed after the gates above were first written:
  * The per-song action sheet ("Change Title", "Start radio", "Play next" …) rendered in Material's
    default sans while the list behind it was set in the display serif, so the most-used menu in
    the app still read as upstream with the palette swapped. The cause was two different menu
    implementations: `Menu.kt`'s `MenuEntry` (which was styled, and is now on the serif row style
    with a quieter icon) and `BottomMenu.kt`, which is the one actually raised for a song and whose
    Material3 `ListItem` headline carried no style at all. Both now use `LumaType.Row`. Verified by
    screenshot (scratchpad/menu_after3.png): entries match the results list behind them.
-->
