# LUMA — MASTER BUILD PROMPT

You are working on **Luma**, an Islamic media app (Quran recitation, nasheeds, lectures, du'a) running on phone, tablet and Car Mode.

There are two bodies of work in this prompt:

- **Track A — UX, navigation and visual quality**: a critical audit of every screen, then fixes.
- **Track B — the intelligence/personalisation engine**: a system that learns the user's listening behaviour.

**Read this entire prompt before touching anything.** Then follow the stage order exactly. Do not jump ahead. Do not start writing code in Stage 0 or Stage 1.

---

## CONTEXT TO CONFIRM FIRST

Before Stage 0, record in `DECISIONS.md`: the platform/framework, where the screen files live, and whether there is an existing design token/theme file. If any of those are unclear from the repo, determine it from the code rather than guessing, and note how you determined it.

---

# STAGE 0 — RECON (no code changes)

Produce `docs/audit/00-inventory.md` containing:

1. **Screen inventory.** Every screen, sheet, modal, tab and overlay in the app. For each: file path, route/name, what the user is trying to do there, the single most important action on it.
2. **Navigation graph.** How you get to each screen, and how you get back. Mark every dead end, every screen with no back affordance, and every screen reachable only through 3+ taps.
3. **Component inventory.** Every card, button, list row, header and sheet variant currently in use. Flag duplicates that do the same job differently.
4. **Design token reality check.** Every colour, font family, font size, font weight, corner radius, shadow and spacing value actually used in the codebase, with counts. If there are 14 greys and 6 heading sizes, I want to see that number.
5. **Tap-depth table.** For these actions, how many taps from cold app open: play last item, resume, search, open Now Playing, open queue, favourite current item, reach Car Mode, reach personalisation settings, reach downloads.

No opinions yet in Stage 0. Facts only.

---

# STAGE 1 — AUDIT AND DESIGN (no code changes)

## 1A. UX audit → `docs/audit/01-ux-findings.md`

Go through **every screen from the inventory, one at a time**. Do not summarise groups of screens together. Do not skip screens because they seem minor.

For each screen, write:

- **Purpose** — one line.
- **Primary action** — the one thing this screen exists for.
- **Findings** — each as its own numbered entry with: what's wrong, why it's wrong from a user's point of view, severity (P0 broken/blocking · P1 daily friction · P2 polish), and the proposed fix.

Judge each screen against all of the following. Where a checklist item is fine, say nothing. I only want findings.

**Navigation**: obvious back affordance; predictable back behaviour; no dead ends; no way to get stuck; consistent nav across screens; user always knows where they are; sheets and modals dismissible by the obvious gesture; quick route back to Home and Now Playing; nothing important buried more than two taps deep.

**Hierarchy**: eye knows where to go first; primary action visually dominant; secondary detail not competing; logical grouping; clear section separation; nothing repeated; not overwhelming; nothing important hidden.

**Visual**: colours that work together; consistent typography; no more than two font families; consistent weight usage; consistent spacing scale; proper alignment; consistent card/border/shadow treatment; nothing that looks cheap, generic, dull or unfinished; personality without mess.

**Interaction**: obvious what's tappable; touch targets ≥44pt; destructive actions protected; every interaction gives feedback; clear loading and empty states; sensible transitions; common actions fast.

**Content**: scannable text; clear labels; no jargon; adequate size and contrast; icons legible without guessing; actions clearly labelled.

Then three dedicated sections:

**Phone.** One-handed use, thumb reach, bottom nav placement, touch targets, keyboard behaviour and dismissal, small-screen layout, scroll behaviour, mini-player reachability, search reachability, path to playback. Should feel effortless, not cramped.

**Tablet.** Not a stretched phone. Multi-column opportunities, side navigation, persistent playback, queue accessibility, comically oversized elements, dead empty space, awkwardly stretched content, landscape. Should feel purpose-built.

**Car Mode.** Audit this as someone actually driving, glancing for under a second at a time. Control size, text legibility at arm's length, number of actions to do anything, distraction, unnecessary information, one- or two-action playback control, favourites access, queue access, autoplay comprehension and override, landscape layout, behaviour beside Maps/Waze. If something would be annoying or distracting at 100km/h, it gets redesigned, not tweaked.

Finish 1A with:

- **Top 10 problems in the whole app**, ranked by how much they hurt a daily user.
- **System-level problems** — the things that are wrong across many screens at once (token sprawl, three competing card styles, inconsistent header pattern). These matter more than any individual screen and get fixed first.

## 1B. Intelligence architecture → `docs/audit/02-intelligence-plan.md`

The architecture is already designed and lives at `docs/luma-intelligence-architecture.md`. **Read it fully before writing anything.** Do not redesign it from scratch. Your job in 1B is to reconcile it with the actual codebase:

- Which parts already exist in some form.
- Which parts conflict with the current data model or playback layer, and what has to change.
- Concrete schema for the event log, item facets, and affinity cells in this project's actual storage layer.
- Answers, from the code, to the open questions in §16 of that doc: catalogue size, metadata completeness, video presence, multi-device, discovery pool curation.
- Anything in the architecture you think is wrong for this codebase. Say so plainly with reasoning. I want disagreement where you have it, not compliance.

Non-negotiables from that document, restated so they don't get lost:

- Quran is not the same content class as nasheeds. Skips on Quran can reorder, never suppress.
- Every event carries **provenance** (`manual_browse` / `search` / `autoplay` / `queue` / `playlist` / `resume` / `notification`). Without it the model learns from its own output.
- Positive signals in Car Mode are weighted down heavily; negative signals up.
- Affinity carries strength **and** confidence **and** decay. One action never defines a preference.
- Nothing automatic is ever permanent. Soft suppression expires and is user-visible.
- The ranker is linear and every decision is logged with its score breakdown.
- Transparency and control ship in the *same release* as inference, never later.

---

# CHECKPOINT — Stage 1 complete

No approval needed. Write the following into `docs/audit/01-ux-findings.md` and `DECISIONS.md`, commit, and continue straight into Stage 2:

- Top 10 UX problems
- The system-level problems
- Your batch order for Stage 2, and why you chose it
- Anything in the intelligence architecture you disagree with, with your reasoning and what you're doing instead

Decisions you'd otherwise have asked about go in `DECISIONS.md` under **Assumptions to review**.

---

# STAGE 2 — UX FIXES

Work in **batches**, in this order. One batch at a time. Commit at the end of each batch with a clear message, update `PROGRESS.md`, then continue to the next.

**Batch 1 — System foundations.** Design tokens (single colour ramp, type scale, spacing scale, radius scale, elevation scale). Collapse duplicate components into one. Standardise the header, list row, card and sheet patterns. This is the highest-leverage work in the entire audit and it comes first, because every later fix depends on it.

**Batch 2 — P0 navigation.** Dead ends, missing back, unpredictable back, undismissable sheets, anything a user can get stuck in.

**Batch 3 — P1 hierarchy and depth.** Clutter, competing actions, buried features, excessive tap depth on the actions in the Stage 0 tap-depth table.

**Batch 4 — Car Mode.** Treat as its own product surface, not a responsive breakpoint.

**Batch 5 — Tablet.** Purpose-built layouts.

**Batch 6 — P2 polish.** Spacing, alignment, transitions, empty and loading states, microcopy.

### Rules for Stage 2

- **Do not rewrite screens that are working just to apply a personal aesthetic.** Every change must trace to a numbered finding in `01-ux-findings.md`. If you want to change something that isn't in the findings, add it to the findings first with a reason.
- Do not add dependencies without asking.
- Do not invent a new visual language. Refine what's there into something coherent.
- Update `01-ux-findings.md` as you go: mark each finding fixed, deferred or rejected, with a one-line note.
- Keep diffs reviewable. If a batch is getting large, split it across several commits.
- After each batch: re-walk the affected screens against the rubric and report anything the fix broke or newly exposed.

---

# STAGE 3 — INTELLIGENCE ENGINE

Follow the phase order in the architecture doc. Commit and update `PROGRESS.md` between phases, then continue.

**P0 — Foundations, no inference.** Event log with provenance. Resume position. Last-reciter-per-surah memory. Deterministic continuation (next in series / playlist / sequential Quran). Recently-played exclusion. This alone is most of what a user perceives as intelligence — build it properly before anything clever.

**P1 — Affinity and ranking.** Facet model with confidence and decay. Hard filters. Linear scorer. Diversity quotas. Decision log. Offline replay harness against the event log.

**P2 — Negative signals and control, shipped together.** Skip interpretation, soft suppression, forgiveness, "What Luma Learned", full control surface including private session, quarantine, per-facet reset and full reset. These are one release. Inference does not ship without the ability to see and undo it.

**P3 — Session intelligence.** Intent blending, engagement tracking, skip-streak circuit breakers.

**P4 — Context.** Prayer-relative time buckets, Ramadan buckets, device modifiers, and the significance gate that stops fabricated patterns.

**P5 — Exploration.** ε schedule, directional bookkeeping, cooldowns.

Do not build P6 (embeddings, collaborative filtering, learned rankers). There is not enough population data for it to be anything but confident noise.

---

# STANDING RULES

- **Be critical of your own work.** After every batch and every phase, re-examine what you built as a first-time user would. Report what's still weak.
- **Identify problems I haven't named.** The checklists are a floor, not a ceiling. If a screen is ugly, dull, generic or awkward and no checklist item covers it, say so and fix it.
- **Disagree with the brief when it's wrong.** If a finding's proposed fix would make something worse, don't build it — write why in `DECISIONS.md` and do the better thing instead.
- **Never interrupt the user to be clever.** No unnecessary popups, no confirmations on non-destructive actions, no notifications derived from inferred behaviour, no commentary on their listening habits. Luma is calm.
- **Powerful without feeling complicated.** Advanced functionality appears when it's useful, not thrown at the user by default.
- **Report honestly.** If a phase is half-done, say half-done. If you're unsure something works, say so instead of asserting it.

The standard for every screen: obvious, smooth, fast, natural, calm, organised. Not merely functional.
