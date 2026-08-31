# Theme Stage 0 — Frutiger Aero study

No code in this stage. Values below are sampled from the reference images
programmatically (median-cut quantisation over a 120×120 resample, plus a vertical
luminance profile), not eyeballed.

Reference folder confirmed at **`final prompts/frutiger areo refernce images/`** — the
theme prompt guessed `frutiger aero reference images`; the real directory is spelled
`areo refernce`. 7 images.

## 1. Reference analysis

### Measured palettes

| Image | Dominant values | Vertical luminance top / mid / bottom |
|---|---|---|
| `images.jpg` (aqua orb) | `#1A899A` `#39A6B3` `#74C1CA` `#54B9C1` `#93C3CC` | 159 / 159 / 154 |
| `images 5.jpg` (Winamp Aero skin) | `#49A8A4` `#C0EAF6` `#9ED9DA` `#D1EEF8` `#DEEDF9` | 169 / 228 / 212 |
| `images 6.jpg` (ice/water) | `#D1E9FA` `#93C7D7` `#4B8DA6` `#C0E2F7` `#BADCE7` | 189 / 185 / 201 |
| `images 2.jpg` (sky/blue) | `#2565E2` `#267DEB` `#76B3F0` `#CCDDF5` `#4E82EA` | 185 / 142 / 137 |
| `images 4.jpg` (device on white) | `#202430` `#D5D6D5` `#495670` `#FEFEFD` `#F3F2F0` | 153 / **229** / 79 |
| `images 3.jpg` (night water) | `#0E2C47` `#28435A` `#19334B` `#0B1522` `#010204` | 26 / 62 / 33 |
| music-player mock | `#15361A` `#426741` `#0B1C0E` | 45 / 63 / 26 |

**What the numbers say.**

- The aesthetic's spine is a narrow aqua band: roughly `#1A899A` (deep) →
  `#49A8A4`/`#4B8DA6` (mid) → `#93C7D7`/`#9ED9DA` (light) → `#C0EAF6`/`#D1EEF8` (near-white
  tint). Saturation stays high right up into the pale end — the pale values are *tinted*,
  never neutral grey. That is the single biggest departure from modern glassmorphism.
- Sky blue (`#267DEB`, `#76B3F0`) is a **separate** family used for backdrops, not for
  controls. Mixing the two families on one control is what makes fake Aero look muddy.
- **The specular band sits in the middle-upper region, not at the very top.** `images 4`
  measures 153 / **229** / 79 top/mid/bottom — the brightest row is the middle and the
  bottom is dark. The recipe is not "white at the top fading down"; it is a bright band
  with a soft falloff both ways and a distinctly darker base.
- Two of seven references are dark (`images 3` at luminance 26/62/33, and the music-player
  mock). Aero Nocturne is deep navy-teal `#0E2C47`/`#28435A`, not black — and the mock
  proves the era also did deep green. Nocturne keeps the same mid-band gloss at much lower
  luminance.

### Gloss, bevel, depth — the recipe

From the Winamp skin (`images 5`), which is the most directly transferable reference
because it is literally a music player:

- **Panel**: rounded rectangle, small radius (≈4–6px at 275px wide ⇒ roughly 2–3% of
  width, i.e. a *tight* corner, not a pill). Aero used small radii on panels and full
  rounds only on handles.
- **Specular**: a light band across the upper-middle of the panel, brightest around 35–45%
  of the height, falling off both ways. White at roughly 35–45% opacity over the tint.
- **Edge**: a 1px light edge on the top and left, a 1px darker edge on the bottom and
  right. This is the bevel, and it is what reads as "physical" more than the gloss does.
- **Handles** (the equaliser sliders): full circles, saturated cyan body, a small bright
  highlight in the upper third, a soft drop shadow beneath. This is exactly the scrubber
  handle Luma needs, and the theme prompt is right that the scrubber is where this
  aesthetic earns its keep.
- **Text**: on every reference, body text sits on a solid or near-solid panel. Nothing in
  any reference puts a label over photography.

## 2. Asset inventory

**`/home/elyas/Documents/Synilogix.com/frutiger_assests` — 1284 PNGs, 1.3 GB.**

| Category | Files | Size | Usable for |
|---|---|---|---|
| foregrounds | 141 | 322M | — mostly full scenes, too heavy |
| water | 69 | 188M | Nocturne backdrop, Full-intensity headers |
| trees | 103 | 120M | — off-brief for a Quran app |
| buildings | 155 | 95M | — |
| skyboxes | 64 | 89M | **Full-intensity backdrops** (Home, Discover, Car-free surfaces) |
| metro | 79 | 62M | — |
| objects | 181 | 57M | — |
| bubbles | 43 | 55M | **Idle ambient ornament** (Home only, pauses on background) |
| insects / sealife / animals | 121 | 116M | — off-brief |
| clouds | 37 | 43M | **Full-intensity backdrops** |
| flares | 32 | 21M | Light bloom overlay, sparingly |
| globes / balloons / airplanes / people / furniture / misc | — | ~110M | — |

Sampled dimensions show the problem plainly: `skyboxes_1.png` is 2300×3500 at 753 KB,
`water_11.png` is 7038×4716 at **17 MB**, `bubbles_11.png` is 2828×2828 at 2.4 MB. The
theme's own hard gate is **400 KB per shipped background**.

**Selection rule adopted**: at most one backdrop per intensity level plus one bubble
sprite sheet; downscale to 1440px on the long edge (xxhdpi phone/tablet is the largest
surface that shows a backdrop full-bleed); convert to WebP quality 82; strip metadata.
Measured budget and the exact files chosen are reported at the end of theme Stage 3.

Everything not selected stays out of the repository. The asset folder is **not** vendored.

## 3. What is missing

The aesthetic needs these and the folder does not contain them:

1. **A gloss/bevel sprite or 9-patch.** Every control treatment has to be drawn
   procedurally in Compose (gradient + border + shadow) because there is no source
   material for it. This is fine and arguably better — it scales and tints — but it means
   the gloss is code, not asset, and must be a token or it will drift (theme prompt's own
   warning).
2. **Anything Islamic.** The folder is a general 2000s-era asset library: aeroplanes,
   furniture, insects, trees. There is no geometry, no arabesque, no mosque silhouette,
   nothing that suits a Quran surface. Minimal intensity therefore uses **no imagery at
   all**, which happens to be the correct answer anyway.
3. **Dark-mode source imagery.** Only `images 3` is a night reference and the folder's
   skyboxes are daylit. Aero Nocturne backdrops will be derived by re-grading a daylit
   skybox rather than sourced, and that compromise is recorded here.

## 4. Typography check required by the prompt

The prompt asks specifically whether the current font is geometric. **It is not — the app's
display face is Instrument Serif, a serif.** That is a different mismatch than the one the
prompt anticipated: Aero-era chrome was humanist *sans* (Frutiger, Segoe UI), so a serif
pulls against the era at least as hard as a geometric sans would.

Per the prompt the font stays. The compensation is to lean entirely on colour, gloss,
bevel and depth — which is also why the missing radius and elevation scales
(`01-ux-findings.md` X1) block this track and are Batch 1.

**Arabic**: the app renders Arabic UI text in the same stack; mixed-script rows were
observed truncating from the *left* in the queue (finding 9). No Uthmanic script face is
present and no mushaf/ayah surface exists in this codebase — the app is a music client, so
the prompt's "ayat must not render in the UI font" rule has nothing to bind to yet. Flagged
rather than invented.
