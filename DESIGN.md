# Kreate — design and product thinking

Written before implementing the redesign, so the work has something to be judged against.

---

## Who opens this app, and why

Three journeys account for almost everything, and they are not equally weighted.

**1. "Put something on." (~70% of opens)**
The user already knows roughly what they want — the thing they were listening to, or something
like it. They are walking, driving, working. They want sound in under three seconds and they do
not want to browse. This is the dominant case and the current app serves it *worst*: it opens on a
library list and makes you go find something.

**2. "Play this specific thing." (~20%)**
A named track, reciter, or nasheed is in their head. They want search, and they want it to
remember — the same searches recur constantly.

**3. "Show me something." (~10%)**
Genuine browsing. Rare, but this is where affection for an app is built.

The interface currently treats all three as equal. It should not. Journey 1 deserves the top of the
home screen and the first tap in Car Mode; journey 3 deserves the beauty.

## What becomes annoying after months

This is the question that matters most, and it is the one generic redesigns never ask.

- **Being recommended the same thing you keep skipping.** The single most infuriating behaviour a
  media app has. If someone skips a nasheed four seconds in, three times, the app must stop
  offering it. Silently, without being asked.
- **Repetition.** A rotation that feels like fifteen tracks wide gets old in a week.
- **Losing your place.** Abandoning a 90-minute recitation and being offered it from zero.
- **Re-deciding.** Making the same choice every morning is the app failing to notice a habit.
- **Clutter that never earns itself.** Fourteen unlabelled icons on a library screen.

## What the app should learn — and what it must not

**Should learn (behaviour, no configuration):**
- Skips, weighted by *speed*. A skip at 4 seconds is a rejection. A skip at 80% is "heard it".
- Completions and, more strongly, replays. A replay is the clearest signal a user ever gives.
- Queue removals — an explicit, deliberate rejection, worth more than a skip.
- Time-of-day patterns. Quran at 05:30 and nasheed at 20:00 are different modes of the same person.
- Device. Car sessions are long and hands-off; phone sessions are short and interactive.

**Must not do:**
- Never *hide* content. Learning may reorder suggestions; it may never make a library item
  unreachable. Search must always find everything.
- Never act on one data point. Three consistent rejections before anything changes.
- Never be mysterious. Every learned preference must be visible in one place and undoable in one
  tap. If the app cannot explain a decision, it should not make it.

The honest framing: **the app is allowed to change what it _offers_, never what the user can
_reach_.**

## What should always be one tap away

From a cold start, on any device: **resume**, **play/pause**, **skip**, **search**.
Everything else can be two.

In Car Mode that shortens further — resume, transport, and the up-next list must be visible without
any tap at all, because a tap while driving costs attention that isn't spare.

## How devices genuinely differ

Not screen sizes. Different *situations*.

| | Phone | Tablet | Car centre display |
|---|---|---|---|
| Posture | one hand, moving | two hands, seated | one hand, eyes elsewhere |
| Session | minutes | long, attentive | very long, hands-off |
| Attention | partial | full | **almost none** |
| Wants | resume fast | browse, discover | resume, skip, and nothing else |
| Failure that matters | fiddly targets | wasted space | *anything* needing a second look |

This is why Car Mode is a separate surface rather than a responsive breakpoint. A breakpoint
changes proportions; the car needs different *content* — fewer choices, larger targets, no modals,
no text under 18sp.

## Why themes, and why they must differ by more than colour

A theme is not a palette. Recolouring one design ten times produces ten versions of the same app,
and the user can feel that immediately even if they can't name it.

Real differentiation needs four axes:

1. **Colour** — the obvious one.
2. **Material** — how a surface behaves. Flat, glossy, glass, paper, brushed metal. This is what
   makes Frutiger Aero feel like Frutiger Aero and not "blue".
3. **Motion** — personality in time. Aero overshoots and settles like water; a luxury theme moves
   once, precisely, and stops. Same interaction, entirely different feeling.
4. **Shape** — corner radius and density.

So the token layer has to carry all four, or "10 themes" is a lie told with hex codes.

## What "alive" actually means here

Not particle effects. Three things:

- **The app reacts to its content.** Artwork should influence the surface it sits on.
- **The app reacts to the user.** A greeting that knows it's 6am. A home screen that leads with
  what you actually resume.
- **The app reacts to itself.** Playback state visible in motion, not just in an icon.

## Deliberate non-goals

- No onboarding carousel. The app should be understandable without being explained.
- No engagement mechanics — streaks, badges, nudges. This is a media player, not a game.
- No suggestion that interrupts playback.
- No cloud account requirement. Sync is device-to-device (hence QR), and works offline on a shared
  network. The library is the user's, and it stays on their hardware.

---

# The visual language — "emanation"

Added in the redesign pass. `DESIGN.md` above says what the app is *for*; this says what it *looks
like*, and why those are different problems that needed solving separately.

## The problem the earlier passes did not solve

The first redesign fixed the information architecture — home leads with resume, the library became
shelves instead of a taxonomy switch, the player lost four duplicate controls. All correct, and none
of it changed the verdict, which was that the app still looked like the one it was forked from.

That verdict was right. Pull up the home screens of Spotify, Apple Music, Deezer and YouTube Music
side by side on Mobbin and they are genuinely hard to tell apart: a bold sans heading, a horizontal
shelf of rounded squares, a tab bar. Every one of those apps has a different palette and a different
typeface, and it makes no difference at all — **the silhouette is the same, and the silhouette is
what a person actually perceives.** Restyling that layout produces a fifth member of the set.

So this pass changes shapes, not finish.

## Three commitments

**1. Circles carry the focus.** The player's artwork is a disc and playback is the arc around it —
one object where every other player has a square and a slider beneath it. The transport is three
rings, only the play control filled, so there is exactly one focus rather than a row of equal blobs.
An arc also happens to be the most glanceable progress indicator there is, which matters most in the
car.

**2. The arch is the tile.** A semicircular top on a squared base (`LumaShape.Arch`). Anything the
user picks *from* is an arch — it reads as a window you are looking through rather than a card you
are tapping. Corners are percentages, not dp, so the curve stays a true half-circle at any tile
width; a fixed radius flattens into a rounded rectangle as the tile grows, which is the shape being
avoided. The player answers with a disc, so home and player share a language without wearing the
same shape.

**3. Type is the texture.** Instrument Serif at display sizes, set tight, with artwork allowed to
crowd it — against very small wide-tracked sans caps. That pairing is the oldest premium-print
signature there is and it costs nothing. The serif ships in **one weight** deliberately: a display
face with five weights invites a hierarchy built out of boldness, which is exactly the generic look
being avoided. Hierarchy comes from size and space.

## The screen is lit by the record

One colour is pulled from the artwork and bled across the whole screen as a slow-drifting radial
emanation — the literal drawing of the mark, and the cheapest possible way to make an app feel alive
without a single particle effect.

Two rejected alternatives, both worth writing down:

- **Not a blurred cover.** Blurring a bright sleeve yields a bright screen, which is how the player
  once ended up with near-white text on near-white blur. A single extracted hue can be *clamped* to
  a chosen saturation and lightness, so contrast becomes a property of the design rather than of
  whatever the artist's cover happened to be.
- **Not a flat wash.** A flat artwork-coloured field is striking but static, and a solid saturated
  colour at full-screen scale is tiring. A radial that falls off to the ground colour keeps the drama
  at the focus and leaves the edges calm.

The ground is warm near-black rather than `#000`, because pure black flattens artwork against it and
gives the emanation nothing to be brighter *than*.

## Where it lives

`themed/luma/` — `LumaDesign.kt` (type, shape, motion, colour), `LumaAtmosphere.kt` (artwork accent
extraction and the emanation), `LumaComponents.kt` (disc, ring button, arch tile, artwork).

The layer is **additive**. It does not touch `Typography` or `colorPalette()`, which between them are
referenced across most of the 700-odd files inherited from four code generations. Screens opt in;
nothing breaks by ignoring it.
