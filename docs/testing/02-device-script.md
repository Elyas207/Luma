# Manual device script

Everything that cannot honestly be automated in this environment. About 30 minutes with a
real phone, a car, and a Bluetooth device. Each step is pass/fail — write the result and
the device next to it.

**Why these are manual**: this run had two x86_64 emulators and no physical device. An
emulator cannot make a real phone call, cannot pair Bluetooth, has no battery, and its
frame timings do not represent a mid-range Android phone. Automating any of these here
would have produced a test that passes without testing anything.

Device used: ____________________  Android version: ______  Date: __________

## A. Interruptions

| # | Step | Expected | Pass |
|---|---|---|---|
| A1 | Play a track. Receive a real phone call. | Audio pauses before the ringtone is audible. | |
| A2 | End the call. | Playback resumes within ~2s, same position. | |
| A3 | Decline the call instead. | Same as A2. | |
| A4 | Play a track. Trigger a clock alarm. | Audio ducks or pauses; alarm is clearly audible. | |
| A5 | Play a track, then start a YouTube video in another app. | Luma pauses and does **not** resume by itself when the video ends. | |
| A6 | Play a track with Maps navigating. Let it speak a direction. | Luma ducks under the prompt and returns to full volume after — it must not pause and must not stay quiet. | |
| A7 | Repeat A6 five times in a row. | No drift: volume returns fully each time, no stuck ducking. | |

## B. Bluetooth and headphones

| # | Step | Expected | Pass |
|---|---|---|---|
| B1 | Play over the speaker. Connect a Bluetooth device. | Audio moves to Bluetooth; playback continues. | |
| B2 | Walk out of range until it disconnects. | Playback **pauses**. It must not resume on the phone speaker at full volume. | |
| B3 | Reconnect. | Playback stays paused until asked, or resumes on the same item — either is acceptable, but it must be consistent. | |
| B4 | Unplug wired headphones mid-track. | Pauses immediately. Never blasts the speaker. | |
| B5 | With two Bluetooth devices paired, switch between them. | Audio follows; no duplicate playback. | |
| B6 | Use the headset's play/pause and next buttons. | Both work; state stays in sync with the app UI. | |

## C. Car

Mount the phone as you would actually drive with it. **Do these stationary first.**

| # | Step | Expected | Pass |
|---|---|---|---|
| C1 | Open Car Mode in direct sunlight, screen at max. | Title, artist and transport readable at arm's length in under a second. | |
| C2 | Same at night, screen at minimum. | Nothing glares; nothing is too dim to find. | |
| C3 | Glance-test: look away, look back for one second, find play/pause. | Found without hunting. | |
| C4 | Split-screen with Maps or Waze. | Both usable; Luma's controls do not clip or shrink below a thumb. | |
| C5 | Turn the ignition off, wait 5 minutes, turn on. | Playback resumes on the same item at the same position, or waits — but does not restart the track. | |
| C6 | Android Auto head unit, if available: browse and play. | The queue and metadata appear correctly. | |
| C7 | Drive a familiar route (passenger operating the phone). | Nothing demands attention; no popups, no confirmations. | |

## D. Lifecycle and battery

| # | Step | Expected | Pass |
|---|---|---|---|
| D1 | Play a long recitation. Background the app for 10 minutes. | Still playing, notification controls work. | |
| D2 | Background for 10 hours overnight, screen off. | App either still playing or cleanly resumable; resume position correct to within 20 seconds. | |
| D3 | Force-stop mid-track, relaunch. | Home shows "Pick up where you left off" with the right item and position. (Automated as far as an emulator allows; confirm on real hardware.) | |
| D4 | Enable battery saver, play for 15 minutes. | No stalls, no silent death. | |
| D5 | Measure battery over one hour of Car Mode, screen on, mid brightness. | Record the percentage drop: ______ %. Budget is ≤6 %/h. | |
| D6 | Measure over one hour of screen-off audio playback. | Record: ______ %. | |

## E. Network, on real mobile data

| # | Step | Expected | Pass |
|---|---|---|---|
| E1 | Start a track on wifi, walk out of range onto mobile data. | Playback continues or recovers within a few seconds. | |
| E2 | Enable airplane mode mid-track. | A clear message; no silent stall, no crash. | |
| E3 | Disable airplane mode. | Recovers without needing a restart. | |
| E4 | Play in a genuinely weak-signal area (lift, basement, train). | Buffers visibly rather than dying; recovers when signal returns. | |
| E5 | Download a track, enable airplane mode, play it. | Plays from local storage. | |

## F. Accessibility, on the real device

| # | Step | Expected | Pass |
|---|---|---|---|
| F1 | System font size at maximum, walk Home → Search → Player → Car Mode. | Nothing clipped, nothing unreachable, no overlapping text. | |
| F2 | Enable TalkBack, play something from Home. | Every control is announced meaningfully; nothing is an unlabelled button. | |
| F3 | Enable "Remove animations". | The app still works; no reliance on motion to explain a change. | |
| F4 | Display size at maximum. | Layout holds. | |

## G. Content edge cases

| # | Step | Expected | Pass |
|---|---|---|---|
| G1 | Play something with a very long Arabic title in a mixed-script list. | Truncates cleanly at the end; no left-side clipping; baselines align. | |
| G2 | An item with no artwork. | A quiet placeholder, not a bright empty plate. | |
| G3 | Empty states: no downloads, no favourites, no history, no search results. | Each explains itself in one line; none is a blank pane. | |
