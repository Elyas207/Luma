# Luma Intelligence Engine — Architecture

Version 0.1 · design phase · no implementation until §14 phases are agreed

---

## 0. Three things that change the design before anything else

**0.1 You do not have a data problem yet, you have a cold-start problem.**
Collaborative filtering, embeddings, learned rankers and bandit posteriors all need population scale. With a niche catalogue and a small user base, every one of those produces confident noise. The entire v1 must be **single-user, content-based, and interpretable**. Everything below is designed so that a population-scale model can be dropped in later without rewriting the event layer.

**0.2 Quran is not the same content class as nasheeds, and treating it as one will make Luma feel broken.**
People skip surahs for reasons that have nothing to do with preference: time available, where they left off, memorisation targets, mood, a specific ayah they wanted. If a skip on Quran feeds the same negative pathway as a skip on a podcast, Luma will quietly start suppressing recitation from a qari the user loves. That is a religiously and emotionally bad failure, not just a bad recommendation. Content class is a **first-class dimension** in every part of this system, and Quran gets asymmetric rules: it can be reordered, it can never be suppressed by inference.

**0.3 Most of the "magical" feeling is not ranking.**
It comes from: resuming exactly where you left off, remembering which reciter you last used for a given surah, continuing a series instead of jumping away, not repeating something you heard an hour ago, and being quiet. Those are deterministic features and they are cheap. Ranking sophistication is maybe 20% of perceived intelligence. Build the 80% first (Phase 0) or the clever parts will be polishing a car with no engine.

---

## 1. System shape

```
┌──────────────────────────────────────────────────────┐
│ CAPTURE          event log (local, append-only)      │
├──────────────────────────────────────────────────────┤
│ INTERPRET        evidence extraction: event → signed  │
│                  evidence + reliability weight        │
├──────────────────────────────────────────────────────┤
│ MODEL            facet affinities (strength +         │
│                  confidence + decay), context         │
│                  modifiers, suppression register      │
├──────────────────────────────────────────────────────┤
│ SESSION          live intent state, engagement,       │
│                  interaction mode, circuit breakers   │
├──────────────────────────────────────────────────────┤
│ SELECT           candidates → filter → score →        │
│                  diversify → explore → decision log   │
├──────────────────────────────────────────────────────┤
│ SURFACE          "What Luma Learned", controls,       │
│                  per-decision explanation             │
└──────────────────────────────────────────────────────┘
```

Strict one-way flow. **The session layer never writes to the model directly.** It writes events; the model recomputes from events. This is what stops one unusual evening from permanently rewriting a profile, and it means every derived value is reproducible from the log, which makes debugging and offline tuning possible.

---

## 2. Event model

Append-only. One table, local SQLite.

```
event {
  id            ulid
  ts            int (epoch ms, + tz offset stored separately)
  session_id    ulid
  type          enum
  item_id       string?
  position_ms   int?        // where in the item
  duration_ms   int?        // item length at time of event
  source        enum        // PROVENANCE — see below
  slot          int?        // position in the queue/rec list
  context       blob        // frozen snapshot, see §7
}
```

### Event types
`play_start`, `play_end` (with `completion_pct`), `skip_next`, `skip_prev`, `seek`, `pause`, `resume`, `abandon` (app closed mid-item), `replay`, `queue_add`, `playlist_add`, `playlist_remove`, `favourite`, `unfavourite`, `follow`, `unfollow`, `search`, `search_result_open`, `download`, `dislike`, `block`, `impression` (shown, not opened).

### `source` is the most important field in the schema
`manual_browse`, `search`, `playlist`, `queue`, `autoplay`, `recommendation`, `notification`, `resume`, `external` (CarPlay/Android Auto voice, widget).

Why it matters: if you learn from autoplay-driven plays at the same weight as user-chosen plays, Luma is **learning from its own output**. Within weeks the profile collapses onto whatever the ranker happened to like early. Provenance discounting (§4.3) is the only cheap defence.

### What is deliberately NOT captured
Location, precise timestamps in cloud storage, contact data, any cross-app signal, exact GPS-derived "driving" state (use the OS media-route/Car Mode flag instead). Every field must survive the question: *does this measurably improve a recommendation, and would I be comfortable showing it to the user in plain language?* If not, it does not get logged.

---

## 3. Item model

No embeddings in v1. A facet graph, which is sparse-data friendly and human-readable.

```
item {
  id, content_class, creator_id, series_id?, language,
  duration_band,        // <5m, 5-20m, 20-60m, 60m+
  topic_tags[],         // aqeedah, seerah, tazkiyah, ...
  surah_id?, juz?,      // quran only
  energy,               // calm / mid / uplifting  (nasheed/lecture)
  added_at, publisher
}
```

`content_class ∈ {quran, dua_dhikr, nasheed, lecture, podcast, short}`

**Affinity is learned over facet values, not items.** With a small catalogue, item-level affinity never accumulates enough evidence to be usable. Facets generalise: 12 completed Mishary tracks teaches you something about the 400 you have not played. Item-level affinity is only tracked once an item has ≥5 interactions, and even then it acts as an adjustment on top of facet affinity, not a replacement.

Facets are not equally informative. `creator_id` is highly predictive; `language` is nearly deterministic; `duration_band` is context-dependent; `topic_tags` are noisy. Each facet carries a static `informativeness` weight used when aggregating (§5.1). Tune these against the replay harness (§13), do not guess forever.

---

## 4. Evidence extraction: event → learning signal

Each event yields `(evidence e ∈ [-1, +1], weight w ∈ [0, 1])`. Evidence is direction and magnitude; weight is *how much you trust that reading*. Separating these two is what stops ambiguous events from doing damage.

### 4.1 Base table

| Situation | e | w | Reasoning |
|---|---|---|---|
| Completed ≥90% | +0.6 | 1.0 | Strongest passive positive |
| Completed 60–90% | +0.30 | 0.8 | Good but they left |
| Left at 30–60% | 0.0 | 0.3 | Genuinely ambiguous, do not guess |
| Skip after >70% | +0.20 | 0.5 | They got what they came for |
| Skip at 30s–30% | −0.30 | 0.6 | Mild mismatch |
| Skip at 5–30s | −0.50 | 0.8 | Clearest dislike window |
| Skip <5s | −0.20 | 0.25 | Mostly misclick/browsing |
| Skip <2s after screen open | — | 0.0 | Discard entirely, navigation |
| Immediate replay | +0.80 | 1.0 | |
| Replayed on ≥3 distinct days | +1.00 | 1.0 | Strongest behavioural signal there is |
| Opened from search | +0.40 | 0.9 | Intent, not just consumption |
| `queue_add` | +0.60 | 0.9 | Deliberate future commitment |
| `playlist_add` | +0.80 | 1.0 | |
| `playlist_remove` | −0.60 | 0.9 | |
| `favourite` / `follow` | +1.00 | 1.0 | Explicit, no decay (§6) |
| `dislike` | −1.00 | 1.0 | Explicit → hard filter, not a score |
| Impression, not opened | −0.05 | 0.15 | Capped at 3/session, else you punish everything you display |

### 4.2 Modifiers (multiply `w`)

| Modifier | ×w | Reasoning |
|---|---|---|
| `source = autoplay` on a positive | 0.7 | Passive acceptance ≠ endorsement |
| Car Mode, positive signal | **0.4** | Skipping is expensive and unsafe while driving. Absence of a skip means much less here. This is the single most over-trusted signal in car listening. |
| Car Mode, negative signal | 1.2 | Conversely, a skip while driving is *deliberate* and costly. It means more. |
| Session skip-streak active (§8.3) | 0.3 | Mood mismatch, not item dislike |
| Session flagged anomalous (§12.3) | 0.2 | Someone else's hands, or an unusual night |
| `content_class = quran`, negative | 0.3 | See §0.2 |
| Item is new to the user | 1.1 | First impressions carry information |

### 4.3 Attribution
Evidence from an event is applied to **every facet of the item**, scaled by that facet's `informativeness`. So a completed Mishary Al-Afasy recitation of Al-Mulk credits: creator (high), content_class (high), duration_band (low), time-context (only if §7 gate passes), item (if eligible).

---

## 5. The affinity model

### 5.1 Storage and update

Per `(facet_type, facet_value)`:

```
affinity_cell {
  S        float   // decayed weighted evidence sum
  W        float   // decayed weight sum
  t_last   int
  n        int     // raw event count, for display only
}
```

Update on new evidence `(e, w)` at time `t`:

```
λ  = 0.5 ^ ((t - t_last) / half_life)
S  = S*λ + e*w
W  = W*λ + w
t_last = t
```

Read:

```
strength    = S / max(W, ε)          ∈ [-1, +1]
confidence  = W / (W + k)            ∈ [0, 1)
effect      = strength * confidence
```

Three properties this buys you, all for about ten lines of code:

- **Cold start is automatic.** One event gives `W ≈ 1`, so with `k = 8`, confidence ≈ 0.11. A single strong action moves ranking by roughly a tenth of what it "says". This is exactly the "one action should rarely define a preference" requirement, expressed as maths rather than as a special case.
- **Decay is automatic** and does not need a cron job. It happens on read/write via `λ`.
- **Contradiction is handled.** A user who loves a creator but skipped three tracks ends up with a moderate strength and a *high* confidence, which is the honest reading.

`k` per facet type: creator 8, content_class 5, topic 15, item 4, context cell 25 (deliberately high, see §7).

### 5.2 Half-lives

| Layer | Half-life | Why |
|---|---|---|
| Explicit preferences | ∞ (no decay) | Only the user changes them |
| Creator / content_class | 90 days | Taste moves slowly |
| Topic tags | 45 days | Interest cycles are faster |
| Item-level | 30 days | Mostly used for repetition control |
| Context modifiers | 60 days | Routines change with seasons and semesters |
| Session intent | ~3 items | See §8 |

### 5.3 Change detection
Decay alone is too slow when a real shift happens (new job, Ramadan, a new madhhab of taste). Run a cheap detector monthly per facet: compute strength over the last 30 days vs the trailing 90. If the sign flips **and** the recent window has `W ≥ 10`, halve that cell's half-life for 60 days. Fast adaptation only when there is actual evidence of a change, not on every wobble.

---

## 6. Explicit vs inferred: hard separation

Two registers, never mixed in storage:

- **Explicit**: favourites, follows, dislikes, blocks, language settings, "always ask before autoplaying lectures". No decay. Only changed by the user.
- **Inferred**: everything in §5.

Rules:
1. Explicit **always** overrides inferred at ranking time.
2. Inferred can never produce a hard exclusion. It can only reduce a score, floor −0.6, or place an item in the **soft-suppression register**, which is visible and reversible.
3. When inferred contradicts explicit (favourited creator, but skipping their new uploads), do not fight the user. Downrank the specific items, keep the creator surfaced, and after enough evidence surface a gentle prompt in "What Luma Learned": *"You've been skipping recent uploads from X. Keep recommending them?"* Ask once. Never ask twice about the same thing.

---

## 7. Context, and the gate that stops it being garbage

Context is where personalisation systems most often produce confident nonsense. One lecture at 3am should not create a "3am personality".

### Buckets
- **Time**: prayer-relative, not clock-relative. `pre_fajr, post_fajr, morning, midday, post_asr, maghrib_isha, late_night`. For this app that is a far better behavioural unit than "8am", it self-adjusts through the year, and it matches how the user actually thinks about their day. Requires prayer times, computed locally from coarse location, cached.
- **Day type**: weekday / weekend / **Friday** (distinct: Kahf, khutbah, different rhythm entirely).
- **Season**: `ramadan`, `last_ten`, `normal`. Ramadan behaviour is so different it should not contaminate the annual model. Treat Ramadan events as a separate context bucket with its own cells, and reuse them next Ramadan. That alone will feel uncanny.
- **Device**: phone / tablet / car / cast.
- **Session depth**: first item / mid-session / long-session (>45 min).

### The significance gate
A context cell contributes to ranking **only if all three hold**:
1. `n ≥ 30` events in that bucket
2. `confidence ≥ 0.5`
3. `|strength_in_bucket − strength_global| ≥ 0.15`

Otherwise the context term is exactly zero and the global model runs. Most contexts, for most users, will never pass this. That is correct and intended. A user with genuinely no morning/night distinction should not be given a fabricated one.

### Device is a modifier, not a personality
One unified user model, with device applying: candidate filters (no long-form video in car), exploration caps (§10), evidence weights (§4.2), and diversity tolerance (car mode prefers familiar). Never a separate profile.

---

## 8. Session intelligence

Session = activity with gaps <30 min. Held in memory; only aggregates survive.

```
session_state {
  seed_source, seed_item
  intent[]          // EWMA over facets of played items, α=0.5 (~3 item half-life)
  engagement        // rolling, see 8.2
  interaction_mode  // lean_in | lean_back
  consecutive_skips
  autoplay_depth
  explored_this_session, exploration_outcomes
}
```

### 8.1 Intent vs profile
Final affinity used in scoring:

```
blend = (1 - β)*long_term_effect + β*session_intent
```

`β` starts at 0.35 and moves:
- +0.25 if session facet entropy is low (they are clearly on one thing right now)
- +0.15 if the seed was a search (explicit current intent)
- −0.20 if engagement is falling
- clamp [0.15, 0.70]

Never 1.0. Even a laser-focused session should not fully erase the profile, because that is how you get stuck in a rut the user cannot escape by pressing "next".

### 8.2 Engagement
`engagement = EWMA over last 8 items of per-item outcome ∈ [-1,1]` (completion +1, mid 0, early skip −1). Drives `β`, exploration rate, and diversity tolerance.

### 8.3 Circuit breakers
- **3 consecutive early skips**: enter *recovery*. Exploration → 0. Drop to highest-confidence, previously-completed, recently-unplayed content. Reduce all skip evidence weight to 0.3 for the rest of the session (this is mood, not taste). If `interaction_mode = lean_in`, stop autoplaying entirely after the next item and let them drive.
- **5 consecutive early skips**: stop autoplay. Surface a quiet, one-line, dismissible affordance: *"Something else?"* with 3 safe choices. Do not popup, do not apologise, do not explain.
- **Recovery success** (2 completions) exits the state and restores normal weights.

This directly answers "what happens when the algorithm is very confident but wrong": it notices within three items and gets out of the way. Speed of recovery matters more than initial accuracy.

---

## 9. Autoplay: candidates → filter → score → diversify

Triggered on `play_end`. Budget: <50ms, must run offline.

### Stage 0 — Deterministic continuation (checked first)
If any of these hold, play it and skip the whole pipeline:
- Unfinished series/playlist/album with a clear next item
- Multi-part lecture
- Sequential Quran listening (user played 3+ consecutive surahs/juz)

Nothing a ranker produces beats "the next episode". A surprising number of "smart" systems get this wrong and it is the most noticeable failure a user can experience.

### Stage 1 — Candidate generation (~200, tagged by source)
| Generator | Cap | Notes |
|---|---|---|
| Same creator, unplayed | 40 | |
| Same series/album | 20 | |
| Facet-similar (Jaccard over topic+class+energy+language) | 50 | |
| Favourites not played recently | 25 | The reliable fallback pool |
| Session-intent similar | 30 | Uses `intent[]`, not the profile |
| Historically completed, cold for >60d | 20 | Rediscovery, cheap and high-hit-rate |
| Discovery pool | 15 | Curated + long-tail from high-affinity facets with low coverage |

Generator tag is retained; used for diversity quotas and for the decision log.

### Stage 2 — Hard filters
- Explicit dislike / block / muted creator
- Language not in user's set
- Recently played, class-dependent: nasheed 7d, lecture 30d, podcast 30d, short 3d, **quran 2h only** (deliberate repeat is normal and healthy here)
- Mode-inappropriate: video-primary in car; >30 min in a session pattern of <10 min bursts
- User content-class exclusions (e.g. "never autoplay podcasts")
- **Pool guard**: if fewer than 15 candidates survive, relax recency windows by 50% and retry. A thin catalogue must never cause dead-air. Log this; it means the catalogue is the bottleneck, not the algorithm.

### Stage 3 — Scoring (linear, interpretable, logged)

```
score =  1.00 * blended_affinity        // §8.1
       + 0.85 * continuity              // same creator/series as just-played
       + 0.45 * context_fit             // §7, usually 0
       + 0.35 * similarity_to_last
       + 0.20 * rediscovery_bonus       // loved, then cold
       + 0.15 * novelty                 // never played
       + 0.10 * freshness               // recently added
       - 0.60 * repetition_penalty      // log-scaled plays in 30d
       - 0.90 * suppression_penalty     // §11
       - 0.40 * generator_risk          // discovery pool costs something
```

Linear on purpose. A gradient-boosted model would score maybe 5% better on a dataset you do not have, and would make §12 (explanations) impossible. Every weight is tunable in one file and every decision is explainable as a sorted list of contributions. Keep it linear until you have logged decisions in the hundreds of thousands, then train on that log (§14 P5).

### Stage 4 — Diversity and selection
1. Take top 25.
2. MMR redundancy penalty against the last 5 played and against already-selected queue items.
3. Hard quotas: max 3 consecutive same creator, max 5 same topic in 8, max 1 discovery item in 4.
4. **Softmax sample from the top 5** rather than always taking #1. Temperature scales with engagement. Deterministic #1-picking is what makes a system feel mechanical, and it is also how you get stuck in a loop where a single item wins forever.
5. Exploration override (§10) may replace the pick.
6. Write a `decision_log` row: candidates considered, chosen, full score breakdown, session state. This powers the "why this?" UI, the offline harness, and future model training. Do not skip it, it is free at write time and impossible to reconstruct later.

---

## 10. Exploration

```
ε = 0.08
  × new_user_boost      (2.5 → 1.0 over first 200 plays)
  × engagement_factor   (0.2 if engagement < 0, 1.4 if > 0.6)
  × facet_success_rate  (per-direction, see below)
  × device_factor       (car: 0.4, phone: 1.0)
clamp [0.02, 0.25]
```

**Never zero.** A floor of 2% is what stops the profile from calcifying and is the only escape hatch when the model has quietly got something wrong.

### Rules of good exploration
- Explore **after a completed item**, never after a skip. A satisfied listener tolerates risk; a frustrated one does not.
- Never as the first item after a manual selection. They just told you what they wanted.
- Explore **one facet away** from a known preference, never randomly. New creator in a loved topic; loved creator in an adjacent topic. Random exploration in a religious-content app is not charming, it is jarring.
- Never explore into an unrated/unvetted content class the user has never touched.

### Bookkeeping
Track outcomes per exploration *direction* (facet pair), not globally. Two consecutive failures in a direction → 30-day cooldown on that direction. One success → widen: generate more candidates in that neighbourhood next session. This is a bandit in spirit, implemented as counters. Do not implement Thompson sampling or LinUCB yet; with this data volume the posteriors are dominated by the prior and you will have added a dependency and a debugging nightmare for nothing.

---

## 11. Negative signals and suppression

Three tiers, only one of which is permanent:

| Tier | Trigger | Effect | Reversal |
|---|---|---|---|
| Score penalty | Any negative evidence | Ranked lower | Automatic on positive evidence |
| **Soft suppression** | 3 early skips on distinct days, no positives | Excluded from autoplay, still fully searchable and browsable | Listed in "Reduced", one tap to restore; auto-expires after 90 days |
| Hard exclusion | Explicit dislike/block only | Never surfaced in recommendations | User only |

**Nothing automatic is ever permanent.** Soft suppression expires. That single rule handles the "algorithm was confidently wrong and I can't fix it" failure that makes people distrust recommender systems.

**Forgiveness**: any positive event (completion, replay, favourite, playlist add) zeroes an item's skip counter and applies a ×1.5 multiplier to the next positive evidence for that item's creator. Recovery from a wrong inference should be faster than the inference was.

**Never suppress**: any `content_class = quran` item, anything explicitly favourited, anything in a user playlist.

---

## 12. Transparency and control

Transparency ships **in the same release as inference**, not later. If Luma starts making decisions the user cannot see or reverse, you have built the thing everyone resents.

### "What Luma Learned"
Rendered from the derived model, in plain language with evidence counts:

- **Strong preferences** — "Mishary Al-Afasy · you've finished 34 recitations, most in the last month"
- **Growing** — "Seerah lectures · 6 completed in 3 weeks" *(shown only above confidence 0.35, so it never guesses out loud)*
- **Patterns** — "You usually listen to Quran after Fajr" *(only if §7 gate passed)*
- **Reduced** — every soft-suppressed item, with why, with a restore button
- **Recently learned** — a changelog of profile changes

Every line has: *Undo* · *Not accurate* · *Why?*

### Controls
- Per-line undo and correct
- Reset one facet / one content class / all personalisation
- "Forget the last 24 hours / 7 days / 30 days"
- **Private session** — plays nothing to the log at all
- **"I'm exploring something new"** — quarantines the session's evidence at ×0.2. This is the honest fix for "my taste today isn't my taste".
- Pause learning entirely. Luma still plays; it just stops updating.

### 12.3 Anomaly quarantine
If a session's facet distribution diverges sharply from the profile (KL divergence above threshold) **and** the session is short, auto-quarantine its evidence at ×0.2 and surface it in "Recently learned" as *"Unusual session — not learned from. Include it?"* Handles a friend borrowing the phone, a child, a one-off event, and a hard night that is not representative of anything.

---

## 13. Privacy and data lifecycle

| Layer | Location | Retention |
|---|---|---|
| Raw events | Device only, SQLite | 90-day rolling window, hard delete |
| Derived model | Device; encrypted blob for multi-device sync, opt-in | Indefinite, decaying |
| Decision log | Device | 30 days |
| Aggregate telemetry | Server, opt-in, no item-level | Anonymous counters only |

- No raw event ever leaves the device by default.
- Sync transmits derived cells (facet, strength, confidence), not history.
- Account deletion cascades to derived and telemetry within one cycle.
- Consequence to accept openly: **no collaborative filtering until there is opt-in aggregate data.** That is the right trade for this app and this audience. Revisit only with an explicit, plainly-worded opt-in.

---

## 14. Failure modes and the mitigation for each

| Failure | Mitigation |
|---|---|
| Filter bubble / collapse | Exploration floor, diversity quotas, softmax selection, rediscovery generator |
| Learning from its own recommendations | Provenance discounting (§4.2) |
| Car-mode over-trust of passive listening | ×0.4 positive weight in car |
| Quran suppression | Content-class asymmetry (§0.2, §11) |
| One weird session rewrites the profile | Confidence shrinkage, anomaly quarantine, session→log→model one-way flow |
| Confidently wrong | Skip-streak circuit breaker, soft-suppression expiry, forgiveness multiplier |
| Taste genuinely changed | Decay + change detector (§5.3) |
| Thin catalogue starves autoplay | Pool guard with relaxed recency (§9 Stage 2) |
| Fabricated context patterns | Significance gate (§7) |
| Annoying the user | Never interrupt; never more than one prompt per week; all learning silent by default; every visible claim above confidence 0.35 only |
| Uncanny/creepy | Never state an inference the user has not already demonstrated obviously; no notifications derived from behaviour; nothing about mood, ever |

### Metrics that actually tell you if it works
- **Manual override rate** on autoplay picks — the primary failure metric
- Autoplay session depth (items before exit)
- Skip rate in the first 30s of autoplay items
- Exploration acceptance rate
- Restore rate from the "Reduced" list — high means suppression is too aggressive
- 7/30-day return rate

### Offline replay harness
Because everything derives from the event log, you can replay a real user's history against new weights and diff the decisions. Build this in Phase 1. It is the difference between tuning and guessing, and it costs about a day.

---

## 15. Roadmap

**Phase 0 — Foundations (no intelligence)**
Event log with provenance. Resume position. Last-reciter-per-surah memory. Deterministic continuation (Stage 0). Recently-played exclusion. Ships ~80% of perceived intelligence.

**Phase 1 — Affinity + ranking**
Facet model with confidence and decay. Hard filters. Linear scorer. Diversity quotas. Decision log. Replay harness.

**Phase 2 — Negative intelligence + control (ship together)**
Skip interpretation, soft suppression, forgiveness, "What Luma Learned", full control surface. These are one release. Inference and transparency ship on the same day, always.

**Phase 3 — Session intelligence**
Intent blending, engagement, circuit breakers, adaptive β.

**Phase 4 — Context**
Prayer-relative time buckets, Ramadan buckets, device modifiers, significance gate.

**Phase 5 — Exploration**
ε schedule, directional bookkeeping, cooldowns.

**Phase 6 — Only with population scale**
Item embeddings, collaborative signals, learned ranker trained on the Phase 1 decision log. This is why the log exists from Phase 1.

---

## 16. Open questions to settle before Phase 1

1. Catalogue size and growth rate. Under ~500 items, half of Stage 1 is unnecessary and the pool guard will be firing constantly.
2. Is content metadata (topics, energy, series) reliable and complete? Facet quality caps the entire system. Garbage tags will beat any amount of algorithm design.
3. Does Luma have a video component? Changes car-mode filters significantly.
4. Multi-device from day one, or phone-first? Decides whether sync is Phase 1 or Phase 5.
5. Who curates the discovery pool? An unvetted long-tail in a religious app is a content-safety question before it is a ranking question, and it needs a human answer.
