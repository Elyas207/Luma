# DECISIONS

Judgement calls made during the autonomous run of 2026-08-30, with what was rejected and
why. Anything the owner should actively review is in **Assumptions to review** at the end.

## Context confirmed before Stage 0 (required by the master prompt)

- **Platform**: Kotlin Multiplatform + Jetpack Compose, Android-only in practice.
- **Screens**: split across `app/kreate/android/themed/**` (current) and
  `it/fast4x/rimusic/ui/screens/**` (inherited upstream). Determined by reading
  `NavRoutes.kt` and following each route to its composable, not by guessing.
- **Existing token file**: yes — `themed/luma/LumaDesign.kt` plus a ten-skin layer at
  `themed/skin/`. Colour and type are already centralised; radius and elevation are not.

---

## D1 — The Frutiger Aero theme is built as the Aurora skin, not as a parallel theme

**Decision.** The app already ships a ten-skin system, one of which (`Aurora`,
"Sky, water and glass. Unreasonably optimistic.") is explicitly a Frutiger Aero skin with
a photographic sky ornament, a gloss surface material and a fluid motion personality.
The Aero work deepens that skin and the shared token layer it draws from, rather than
introducing a second, competing theme system.

**Rejected: a standalone Aero theme layer.** It would mean two theme systems in one app,
each able to override the other — which is exactly the class of bug that made light skins
unreadable during playback earlier today (an artwork palette silently replacing a skin's
palette while the skin kept drawing its own backdrop). Adding a third source of colour
truth would guarantee a repeat.

**Consequence to accept.** The three intensities from the theme prompt (Full / Reduced /
Minimal) become a property *within* the skin system, so they apply to every skin, not just
Aurora. A Quran surface is calm under Obsidian too. This is better than the brief asked
for, but it does mean "the Aero theme" is not one file you can point at.

## D2 — Ship a small, curated subset of the asset library

The asset folder is **1.3 GB across 1284 PNGs**. The theme prompt's own hard gate is
"no background image over 400KB shipped". Shipping even one category is impossible.

**Decision.** Select a handful of assets per surface, downscale to the largest device
density actually used, compress, strip metadata, and report the exact added bundle size.
Anything that cannot survive compression at acceptable quality is dropped and listed in
`docs/theme/00-aero-study.md` under what's missing.

**Rejected: generating or substituting assets.** The prompt forbids it explicitly.

## D3 — The intelligence engine is built underneath the existing TasteEngine, not beside it

`service/taste/TasteEngine.kt` already implements an in-memory suppression register with
departure recording, override and forget, and `TasteCentreScreen` already surfaces it.
That is a partial, undocumented Phase 2 with no event log underneath it.

**Decision.** Build the event log, facet model and affinity cells as specified, then
re-point `TasteEngine` at them so the existing UI keeps working and gains real evidence.
Do not delete it and do not run two suppression registers.

**Rejected: greenfield engine in a new package.** It would leave a second suppression
register live in the app with no owner, which is worse than either option alone.

## D4 — No subagents this run

The operating instructions for this environment say not to use the Agent tool unless the
user asks for it, and this brief does not. Everything is done in one session, sequentially.
The cost is wall-clock time on the recon and audit stages, which are the most parallelisable
part of the work.

## D5 — Scope is ordered by the brief, and the report will say what did not land

The brief is several weeks of work for a team. The order in §3 is followed exactly, which
means foundations and the highest-leverage work land first and the tail (later theme
screens, later engine phases, later test tiers) is where any shortfall shows up. Per §9,
partial work is reported as partial rather than dressed up. Nothing is stubbed and called
implemented.

## D6 — Commits carry the owner's identity only

`git config` already resolves to `Elyas207 <elyas07mabrok@gmail.com>`. Per the run brief,
no `Co-Authored-By` or assistant trailers are added to any commit, overriding the default
commit convention for this environment.

## D7 — `.claude/`, `.unlazy/` and `.agents/` are git-ignored

They contain machine-specific absolute paths and local tooling state. Added to
`.gitignore` and removed from the index rather than committed.

---

## Assumptions to review

1. ~~**The repo named in the brief does not match the configured remote.**~~ **Resolved,
   no action needed from you.** `Elyas207/Kreate` no longer exists and `Elyas207/Luma`
   does — the repository was renamed and the local `origin` URL was simply stale. Updated
   the remote to `https://github.com/Elyas207/Luma.git` and pushed
   `autonomous/2026-08-30` there, confirmed by querying the remote rather than by
   trusting the command's exit code.
2. **"Islamic media app" vs the actual catalogue.** All four prompts describe Luma as a
   Quran/nasheed/lecture app. The codebase is a general YouTube Music client with no
   content-class concept; the library in the test emulator contains mainstream music
   alongside nasheeds. The content-class asymmetry the architecture depends on (§0.2 —
   Quran can be reordered but never suppressed) therefore has **nothing to key off**
   today. Assumption taken: implement `content_class` as a first-class field with a
   best-effort classifier (channel/title heuristics) and default `unknown`, and make the
   Quran-protection rule fire on `quran` only. If the catalogue is meant to be
   Islamic-only, that classifier should be replaced by real metadata.
3. **Prayer-relative time buckets need prayer times.** §7 of the architecture requires
   them, computed locally from coarse location. The app requests no location permission
   today. Assumption taken: implement the bucket interface and the significance gate, and
   compute buckets from a local sunrise/sunset approximation with no new permission,
   marking the context layer as degraded until a real prayer-time source is chosen.
   Adding a location permission to a music app unprompted is not my call.
4. **Existing font vs the era.** The theme prompt asks to report if the current font is
   geometric. It is not — the app uses **Instrument Serif** as its display face, which is
   a serif, not a humanist sans. That pulls against the era differently than a geometric
   sans would: Aero-era chrome was humanist sans. Per the prompt the font stays; the
   compensation is leaning harder on gloss, bevel and depth, which is what D1 does.
