# Tier 6 — performance, measured

All figures from **emulator-5556** (Android 14, x86_64, 1080x2400) on 2026-08-31, debug
build unless stated. Read the caveat before the numbers.

## The caveat, first

This environment has two x86_64 emulators and **no physical device**. An emulator running
on a desktop CPU is not a mid-range Android phone: it has more CPU, different GPU
behaviour, no thermal throttling and no battery. So:

- Cold start and memory are **indicative** — the shape is meaningful, the absolute value is not.
- Frame timings are **not transferable**. The theme brief's gate is 60fps on a mid-range
  device, and that is recorded as **untested**, not as passed.
- Battery figures are **impossible** here and are in the manual device script instead.

Anything below that is not marked as measured is not claimed.

## Cold start to interactive

`am start -W`, five runs, force-stopped between each.

| Run | TotalTime |
|---|---|
| 1 | 1655 ms (first run after install, includes some one-off work) |
| 2 | 1431 ms |
| 3 | 1421 ms |
| 4 | 1435 ms |
| 5 | 1407 ms |

**Median 1431 ms**, against a proposed budget of 2000 ms. Passes on the emulator.

## Scroll frame times

`dumpsys gfxinfo` reset, then twelve scripted flings on Home (which is the heaviest
screen: artwork, an atmosphere gradient and a horizontal carousel).

| Metric | Value |
|---|---|
| Frames rendered | 830 |
| Janky frames | 0 (0.00%) |
| Janky (legacy metric) | 20 (2.41%) |
| 50th percentile | 19 ms |
| 90th percentile | 20 ms |
| 95th percentile | 20 ms |
| 99th percentile | 21 ms |
| Missed vsync | 0 |

No dropped frames and a tight distribution — the 99th percentile is 2 ms off the median,
which means nothing is stalling. **On this hardware.** The mid-range gate remains untested.

## Memory

`dumpsys meminfo` after a browse-and-play session:

- **TOTAL PSS 236.7 MB**, RSS 409.7 MB, swap 0.

Against a proposed budget of 300 MB after a two-hour session. This sample is a short
session, so it is a floor rather than the number the budget asks for; a two-hour soak is
in the manual script.

## Time to first audio

From the playback oracle's own timestamps across runs: the media session reaches a moving
playhead **4.4–5.5 s** after the result is tapped, including the InnerTube client sweep,
the cipher step and the first ranged fetch. Against a proposed 6 s budget.

Worth noting what that time is spent on: the client sweep tries WEB_REMIX (which fails
deep validation), then VISIONOS, and each attempt is a network round trip. If the sweep
learned which client last worked and tried it first, this would drop materially. Not done
in this run — recorded in the final report as the highest-value performance work left.

## Bundle size

- Debug APK: 53.2 MB
- **Release APK (R8, resource-shrunk): 18 MB**

No theme assets were shipped, so the theme's "no image over 400 KB" gate is trivially met
and the added bundle size for the theme work is **0 bytes** — the gloss, bevel and depth
are drawn procedurally rather than from bitmaps. That is recorded in
`docs/theme/00-aero-study.md` as a deliberate consequence of the asset library being 1.3 GB.

## What was not measured

- Battery, over any period. No battery on an emulator.
- Frame timings on mid-range hardware.
- Memory after a genuine two-hour session.
- Autoplay decision time — the selection pipeline is implemented but not yet wired into
  playback, so there is no decision to time.
