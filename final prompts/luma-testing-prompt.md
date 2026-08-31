# LUMA — FUNCTIONALITY & RELIABILITY TEST PROMPT

You are testing **Luma**, an Islamic media app (Quran recitation, nasheeds, lectures, du'a) on phone, tablet and Car Mode, with a local personalisation engine.

Read the whole prompt first. Follow the stage order. Do not write a single test before Stage 1 is written up.

Two documents are context, read both before starting:
- `docs/luma-intelligence-architecture.md`
- `docs/audit/01-ux-findings.md` (if it exists)

---

## THE POINT OF THIS

Luma is an audio app. Almost all of its real-world failures will not be in business logic. They will be in:

1. **Playback state** — the state machine desyncing from the actual audio engine.
2. **Interruptions** — calls, alarms, other apps, bluetooth, headphones, ignition off.
3. **Lifecycle** — backgrounding, OS killing the process, resuming hours later.
4. **Network** — losing connection mid-stream, partial downloads, expired URLs.
5. **Data integrity** — the event log losing, duplicating or corrupting events.

A test suite that has 90% coverage of the view models and nothing covering those five areas is worthless for this app. Prioritise accordingly.

---

# STAGE 0 — RECON (no code)

Produce `docs/testing/00-state.md`:

1. **What test infrastructure exists.** Framework, runner, CI setup, fixtures, mocks, how to run everything locally with one command.
2. **Honest coverage reality.** Not the coverage number. Which of the five areas above have *any* coverage, and what the existing tests actually assert. Flag every test that asserts a mock was called rather than that behaviour was correct.
3. **Critical path list.** The user journeys that must never break, ranked. Start with: cold open → play; resume; search → play; download → play offline; queue management; Car Mode session; favourite/unfavourite.
4. **Testability blockers.** Anything in the codebase that can't be tested without refactoring it first (singletons, direct platform calls, untestable time/date handling, hardcoded network). List them; do not fix them yet.

Facts only. No fixes.

---

# STAGE 1 — RISK REGISTER & PLAN (no code)

Produce `docs/testing/01-plan.md`:

**1A. Risk register.** Every way Luma can fail, each with: likelihood, user impact, whether it's currently detectable, and how you'd test it. Rank by likelihood × impact. Work the list below plus anything you find yourself.

**1B. Test plan.** For each risk: the tier it belongs in (unit / integration / harness / device-manual), and the specific assertion. Anything you cannot automate goes into a written manual device script, not silently dropped.

**1C. Performance budgets.** Propose concrete numbers and how you'll measure them: cold start to interactive, autoplay decision time, search response, scroll frame budget, memory after a 2-hour session, battery drain over an hour in Car Mode.

## CHECKPOINT — Stage 1 complete

No approval needed. Commit the ranked risk register and your tier split, note the reasoning in `DECISIONS.md`, and continue into Stage 2.

---

# THE FAILURE CATALOGUE

Work through all of these in Stage 1. These are the ones that actually bite.

### Playback state machine
- Rapid double-tap on play/pause/next — does state desync?
- Skip pressed during buffering
- Seek past the buffered region
- Track ends exactly as the user presses next
- Autoplay fires while the user is choosing something manually
- Queue reordered mid-playback
- Currently playing item removed from the playlist it came from
- Playback speed change mid-item
- Sleep timer expiring mid-item, and during a transition
- Two playback commands from two surfaces at once (lock screen + in-app)

### Interruptions and audio focus
- Incoming call, then call ends — does it resume, and should it?
- Alarm / timer
- Another app takes audio focus, then releases it
- Navigation app ducking audio repeatedly (Maps voice guidance over Quran)
- Bluetooth connect and disconnect mid-item
- Headphones unplugged — must pause, must not blast a speaker
- Car ignition off, then on again 5 minutes later
- Notification sound over playback
- Two Bluetooth devices, switching between them

### Lifecycle
- App backgrounded for 10 seconds / 10 minutes / 10 hours
- OS kills the process mid-playback — what does the user see on relaunch?
- Force-quit during a download
- Crash mid-item: is the resume position correct, are events flushed?
- Screen rotation mid-playback, mid-sheet, mid-search
- Low power mode
- Doze / background restrictions
- Cold launch directly into Car Mode
- Launch from lock screen controls, widget, or voice

### Network
- Offline entirely
- Connection lost mid-stream
- Connection returns — recover or stall?
- Slow/flapping connection (test at 2G speed, not just on/off)
- Airplane mode toggled mid-session
- Streaming URL expires mid-item
- Server 500s, timeouts, and malformed responses
- Partial/interrupted download, resumed
- Download while storage fills up
- Corrupted downloaded file
- Downloads surviving app update

### Data integrity — the intelligence engine
- Events survive a crash. No loss, no duplication.
- Event log is genuinely append-only; nothing rewrites history.
- Provenance is correct on every event. An autoplay-driven play must never be logged as `manual_browse`. Test this specifically — it is the single most damaging silent bug in the system.
- Affinity decay is deterministic and reproducible from the same log.
- Clock changes: timezone travel, DST, manual clock change, device clock skew. Prayer-relative time buckets depend on this and will silently corrupt.
- Ramadan boundary crossing — bucket switches cleanly, doesn't double-count.
- 90-day event pruning removes what it should and nothing more.
- "Forget last 7 days" actually removes the derived effect, not just the raw rows.
- Full reset leaves nothing behind.
- Private session writes zero events.
- Quarantine and weight modifiers apply as specified.
- Schema migration on app update does not corrupt or drop an existing profile.
- Empty profile, single-event profile, and 50,000-event profile all behave.

### Personalisation behaviour (use the replay harness)
Build these as deterministic scenarios over synthetic event logs:
- New user, zero history — does autoplay still produce something sane?
- User who only listens to Quran — is a nasheed ever forced on them?
- User skips 5 in a row — does the circuit breaker fire and exploration stop?
- Skipped item later favourited — does the negative signal actually weaken?
- Loved creator, then 3 skips of their new uploads — creator must not be suppressed.
- Car mode session with zero interaction — must not produce strong positive affinity.
- Catalogue smaller than the recency exclusion window — must not dead-air. Pool guard fires.
- All candidates filtered out — what plays?
- Every context cell below the significance gate — context contributes exactly zero.
- Soft suppression expiry at 90 days.

### UI and edge content
- Empty states everywhere: no downloads, no favourites, no history, no search results, no queue
- Very long titles, creator names, surah names — truncation and layout
- Arabic and English mixed; RTL text in list rows and Now Playing
- Missing artwork
- Item with no metadata / null facets
- Largest and smallest supported screen
- System font scaled to maximum accessibility size — does anything become unusable?
- Dark and light mode, including mid-session switch
- VoiceOver / TalkBack on the primary paths

### Concurrency
- Search typed fast, results arriving out of order
- Multiple downloads at once
- Autoplay decision running while the profile is being written
- User taps favourite twice quickly
- Sync (if present) conflicting with local changes

---

# STAGE 2 — HARNESS AND FIXTURES

Before writing assertions, build what makes them possible:

1. **Deterministic time.** Injectable clock everywhere. Nothing calls system time directly. This is a prerequisite for testing decay, context buckets and sessions at all.
2. **Fake audio engine** you can drive: buffer, stall, end, error, interrupt.
3. **Network layer you can control**: offline, slow, flapping, error codes, expired URLs.
4. **Synthetic event log generator** — build user archetypes (Quran-only, night listener, car commuter, chaotic skipper, brand new) as reusable fixtures.
5. **Replay harness** for the intelligence engine: feed a log, get a decision sequence out, diff against expected. This is also how weights get tuned later, so it earns its keep twice.

Commit the harness, note in `PROGRESS.md` what it can and can't simulate, and continue.

---

# STAGE 3 — TESTS

Write in this order, committing between tiers:

**Tier 1 — Critical paths.** The journeys from Stage 0. End to end, with real state, not mocks all the way down.
**Tier 2 — Playback state machine and interruptions.** The highest-value tier for this app.
**Tier 3 — Data integrity and the intelligence engine.** Harness-driven.
**Tier 4 — Network and lifecycle.**
**Tier 5 — UI edge cases, accessibility, empty states.**
**Tier 6 — Performance budgets.**

---

# STAGE 4 — MANUAL DEVICE SCRIPT

Everything that cannot be automated goes into `docs/testing/02-device-script.md` as a numbered pass/fail checklist I can run in about 30 minutes: real phone call interruption, real Bluetooth, real car, Maps ducking, real lock screen and CarPlay/Android Auto controls, battery over an hour.

Car Mode gets its own section, tested with the device mounted, glancing only.

---

# BUG HANDLING

Every failure you find goes into `docs/testing/03-bugs.md` before it is fixed: reproduction, expected, actual, severity, suspected cause.

**Log every bug before fixing it.** Write the entry first, commit it, then fix it in a separate commit that references the entry. A test run that quietly changes twelve behaviours with no record is unreviewable. Fix in severity order: P0, then P1, then P2. Anything you decide not to fix gets a written reason in the same file.

---

# STANDING RULES

- **A test that asserts a mock was called is not a test.** Assert observable behaviour and resulting state.
- **Every test must fail before it passes.** Break the code, confirm the test catches it, restore. If it never went red, you don't know it works.
- **Never write a test that codifies current buggy behaviour.** If the code is wrong, the test goes in the bug list, not the suite.
- **No flaky tests, no retry loops, no arbitrary sleeps.** If it needs a sleep, the seam is wrong — fix the seam.
- **Do not chase a coverage number.** I would rather have 40 tests that cover the failure catalogue than 400 covering getters.
- **Refactoring for testability is allowed and expected**, but list it before doing it, and keep it separate from test commits.
- **Report honestly.** If a tier is partly done, say so. If you can't test something in this environment, say that instead of writing a test that pretends to.
