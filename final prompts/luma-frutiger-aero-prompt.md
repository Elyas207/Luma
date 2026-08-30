# LUMA — FRUTIGER AERO THEME

You are applying a **Frutiger Aero** visual identity to Luma, an Islamic media app (Quran recitation, nasheeds, lectures, du'a) running on phone, tablet and Car Mode.

Read this whole prompt before touching anything.

## Where the material is

- **Reference images**: `final prompts/frutiger aero reference images/` — study every image here before you design anything. If the folder is at a different path, find it and confirm the path back to me.
- **Assets**: `/home/elyas/Documents/Synilogix.com/frutiger_assests` — the actual textures, backgrounds and graphic elements to use. Inventory this folder first. Do not generate new assets, do not substitute stock images, do not invent elements that aren't in there.

---

## THE ONE THING MOST LIKELY TO GO WRONG

Frutiger Aero is **not** modern glassmorphism. If you apply a neutral frosted blur to everything and call it done, the result will be generic and wrong.

The actual markers of the aesthetic, roughly 2004–2013:

| Real Frutiger Aero | Modern glassmorphism (avoid) |
|---|---|
| Glossy **specular highlight** on the top half of controls | Flat uniform translucency |
| Saturated aqua → white gradients, real colour | Desaturated, neutral, low contrast |
| Visible bevels, inner shadow, 1px light edge | No edges, no depth |
| Deep, soft drop shadows — objects float above a surface | Minimal or no shadow |
| Tinted translucency picking up colour from behind | Neutral grey blur |
| Nature photography: water, droplets, sky, grass, bubbles, fish | Abstract blobs |
| Lens flare, light bloom, wet-look surfaces | Flat |
| *(Era faces were humanist — Frutiger, Segoe UI. We keep the app's existing font, see Typography)* | — |
| Strong front-to-back layering, clear z-depth | Everything on one plane |

Get the gloss, the bevel, the saturation and the depth right and it will read as authentic. Since the typeface is staying as-is, those four are carrying the entire aesthetic — there is no fallback if they're half-done.

---

## THE OTHER THING MOST LIKELY TO GO WRONG

**This is a Quran app.** Frutiger Aero is bright, busy and maximalist. That's great on discovery, nasheeds, library and Car Mode. It is tonally wrong applied at full strength to a mushaf view or a Quran player, where the experience should be calm and reverent.

So the theme runs at **three intensities**:

- **Full** — Home, Discover, Library, playlists, Car Mode, empty states, onboarding. Photographic backgrounds, gloss, bubbles, the works.
- **Reduced** — general Now Playing, search, settings. Gradients and gloss on controls, quiet backgrounds, no photography behind text.
- **Minimal** — Quran player, mushaf/text view, night listening. The palette and the control language survive; the imagery and gloss step back almost entirely. Deep, still, calm.

The user should feel it's one app throughout. The intensity shift should feel like the app respecting the moment, not like two different apps.

---

# TYPOGRAPHY

**Keep the app's existing font. Do not introduce a new typeface.** No Frutiger, no Segoe UI, no font swap of any kind. The type system stays exactly as it is — family, scale, weights, line heights, letter spacing.

This is a deliberate constraint. It avoids a licensing problem and it stops the theme from becoming a rewrite. The aesthetic has to be carried by **colour, gloss, bevel, depth and shadow** instead of by the typeface. That is harder but it is the job.

One thing to check and report in Stage 0: if the current font is a geometric sans (Poppins, Montserrat, Circular and similar), it will pull against the era feel, since Frutiger Aero grew out of humanist type. Do not change it — but tell me, and compensate by leaning harder on the control treatment and colour.

### Arabic and Quran text

Separate from the Latin UI face, and worth getting right:

- **Arabic UI text** (surah names, creator names, labels): whatever the app currently uses, stays. Check that it sits well beside the Latin face in mixed-script list rows — baseline alignment and optical size are where bad pairings show. Report problems, don't unilaterally fix them.
- **Quran text** (mushaf, ayah display): must be a proper Uthmanic script face — KFGQPC Uthmanic Script HAFS or equivalent — not a UI font. If the app is currently rendering ayat in the UI font, flag it as a finding. This is the one place in the app where the theme has no vote.

---

# TEXT LEGIBILITY — the theme never wins against text

This aesthetic has one characteristic failure: it looks great in a screenshot and becomes unreadable in use. Gradients slide text from 8:1 contrast to 2:1 across a single button. Gloss puts a white highlight directly under white text. Photographic backgrounds turn a track title to mush.

Rules, applied everywhere without exception:

- **Text never sits directly on a photograph or a busy texture.** It sits on a solid or near-solid glossy surface that floats above the imagery. If a design needs text over an image, the design changes.
- **Text never sits across a gradient's full range.** Either the gradient sits behind a solid text plate, or the gradient's range is compressed until the worst point still passes contrast.
- **The specular highlight must not run under text.** Gloss belongs on the top portion of a control; the label sits below it or on a flattened region.
- **No text over lens flare, bloom, bubbles or animated elements.** Ever.
- **Test contrast at the extremes, not the average.** Sample the lightest and darkest pixel behind every text element, in both themes.
- **Test with real content**, not lorem: long Arabic surah names, long creator names, mixed-script rows, truncation, maximum system font size.
- **Test in bright sunlight conditions** (max screen brightness, high ambient) and at night (min brightness). A glossy light theme at 2am and a low-contrast one in a car at midday are both real, common cases.

If a text element cannot be made legible without weakening the theme, the theme weakens. There is no version of this where a beautiful screen ships with text you have to squint at.

---

# STAGE 0 — STUDY (no code)

Produce `docs/theme/00-aero-study.md`:

1. **Reference analysis.** Go through every reference image. Extract: the actual colour values in use, gradient directions and stops, highlight placement and opacity, shadow depth and colour, corner radii, bevel treatment, typography, how translucency behaves, how depth is built. Be specific — "aqua gradient" is useless, `#4FC3F7 → #E1F5FE at 105°, specular highlight covering the top 45% at 40% white` is usable.
2. **Asset inventory.** Every file in the assets folder: what it is, dimensions, file size, transparency, and where in the app it could plausibly be used. Flag anything too large to ship as-is.
3. **What's missing.** What the aesthetic needs that isn't in the assets folder, so we can source it rather than have you improvise.

Do not write code in Stage 0.

---

# STAGE 1 — TOKEN SYSTEM (no screens yet)

Build the theme as a **token and component layer** on top of the existing design tokens. Do not rewrite screens to apply it. If the token layer from the UX work doesn't exist yet, build that first — theming an app with 14 hardcoded greys will not work.

Produce a single theme file defining:

**Colour.** Full ramp derived from the references, not guessed. Primary aqua/cyan, secondary green, sky, deep water, plus neutrals. Every colour paired with the text colour that sits on it legibly.

**Gradients.** Named, reusable: `surface-gloss`, `button-primary`, `sky-backdrop`, `deep-water`, etc. Each with stops, angle and where it's used.

**Gloss and depth.** The specular highlight recipe, the bevel recipe, the shadow scale. These three are what make a control look Aero. They must be tokens, not per-component hacks, or the app will drift within a week.

**Type.** See the Typography section below — this is the single highest-risk part of the theme. Existing type scale unchanged. No display font.

**Translucency.** Tint colour, blur radius, and the rule for when live blur is allowed (see performance, below).

**Motion.** Aero-era motion is soft, eased, slightly bouncy — never snappy or linear. Define durations and curves once.

**Dark mode — "Aero Nocturne".** Frutiger Aero is inherently daylit, so dark mode needs a real design, not an inversion. Deep water and aurora rather than sky and grass: navy/teal base, cool cyan accents, gloss preserved but at lower luminance, bloom instead of flare. This matters more than usual here because night Quran listening is a core use case.

## CHECKPOINT — token system complete

No approval needed. Build one reference screen (Now Playing) in light and dark against the tokens, screenshot both into `docs/theme/`, commit, and continue. That screen is your benchmark — every later screen gets compared back to it for consistency.

---

# STAGE 2 — COMPONENT LIBRARY

Rebuild the shared components against the tokens, in this order: button (primary/secondary/icon), list row, card, sheet, header/nav bar, tab bar, slider/scrubber, toggle, mini-player, full player controls.

Rules:

- Every component gets the gloss/bevel/shadow treatment from tokens. One recipe, applied consistently.
- Scrubbers, sliders and progress bars are where the aesthetic shines — glossy track, wet-look fill, a real handle with depth. Spend time here, it's the most-looked-at control in the app.
- Touch targets stay ≥44pt regardless of how the visual treatment shrinks the apparent control.
- Build a component gallery screen so all of them can be reviewed side by side.

---

# STAGE 3 — SCREENS

Apply surface by surface, in intensity order: Full → Reduced → Minimal. Commit after each group, screenshot the group into `docs/theme/`, and continue.

For every screen, backgrounds follow one rule: **photographic and busy behind chrome, never behind body text.** Text sits on a solid or near-solid glossy surface that floats above the imagery. This single rule prevents most of the legibility damage this aesthetic can do.

---

# HARD GATES — non-negotiable

Any screen failing these gets reworked, no matter how good it looks:

1. **Text legibility.** Every rule in the Text Legibility section above, on every screen, in both themes. Body text ≥4.5:1, large text and icons ≥3:1, measured against the lightest and darkest point of whatever sits behind them — not the average. No text on photography, gradients at full range, gloss highlights, or animated elements. Report the worst-case ratio per screen; anything under threshold blocks the stage.
2. **Car Mode.** No photographic backgrounds. No gloss that produces glare. Maximum two levels of visual depth. Text legible at arm's length in direct sunlight. The theme is allowed to shape Car Mode's colour and control language and nothing more. If a driver has to look twice, it's failed.
3. **Performance.** No live blur inside a scrolling list — bake it into an asset. 60fps scrolling on a mid-range Android device. No background image over 400KB shipped. Measure and report frame times before and after theming; if scroll performance regresses at all, fix it before moving on.
4. **Accessibility.** Works at maximum system font size. Works with reduce-transparency and reduce-motion enabled — provide a solid-surface fallback for both. Screen reader unaffected.
5. **Battery.** No continuous animation. Ambient motion (bubbles, shimmer, flare) is allowed on idle screens only, pauses when playback is backgrounded, and never runs in Car Mode.

---

# STANDING RULES

- **Authentic, not pastiche.** Pull specifics from the references. When unsure, go back to the images rather than to a general impression of the era.
- **Restraint scales with reverence.** Quran surfaces get the palette and the control language, not the bubbles.
- **Functional first.** If a treatment makes something harder to read, tap or find, the treatment loses. Every time.
- **Consistency over novelty.** One gloss recipe, one shadow scale, one radius scale. Do not hand-tune per screen.
- **Use the provided assets.** Do not generate images, do not pull stock, do not improvise elements. If something's missing, list it in `docs/theme/00-aero-study.md`, design around the gap, and note the compromise.
- **Optimise everything shipped.** Resize, compress, strip metadata. Report the total added bundle size at the end of each stage.
- **Be critical of your own work.** After each stage, look at the screens as a first-time user in bright sunlight, at night, and while driving. Report what's still weak.
