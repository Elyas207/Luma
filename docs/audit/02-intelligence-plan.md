# Stage 1B — intelligence architecture, reconciled with this codebase

Read against `docs/luma-intelligence-architecture.md` v0.1. The architecture is not
redesigned here; this records what already exists, what conflicts, the concrete schema for
*this* storage layer, and where I think the document is wrong for this repository.

## 1. What already exists

`service/taste/TasteEngine.kt` (189 lines) + `database/models/ListeningSignal.kt` +
`ListeningSignalTable` + `themed/taste/TasteCentreScreen.kt`.

It stores, **per song**: `completions`, `lateSkips`, `fastSkips`, `replays`, `removals`,
`userOverride`, `updatedAt`, with a derived `score` and `isSuppressed`. It already gets
several things right that the architecture also insists on:

- A skip is interpreted by *position*, not treated as a uniform negative
  (`FAST_SKIP_FRACTION = 0.15`, `COMPLETION_FRACTION = 0.85`).
- Reordering only — nothing is removed from search or library.
- Every stored number is renderable as a sentence, so it is explainable and undoable.
- A user override that stops learning for an item *without erasing the observed history*,
  which is a genuinely good detail the architecture does not specify.
- Learning can be switched off entirely.

## 2. What conflicts, and what has to change

| # | Conflict | Why it matters | Change |
|---|---|---|---|
| C1 | **Item-level only.** Affinity is stored per `song_id`. | §3 is explicit that with a small catalogue item-level affinity never accumulates enough evidence, and that facets are what generalise. 12 completed tracks by one reciter currently teach the app *nothing* about their other 400. | Add facet cells; keep item cells as an adjustment above 5 interactions, exactly as §3 specifies. |
| C2 | **Raw counters, no decay and no confidence.** | Violates the non-negotiable "one action never defines a preference". Today, one fast skip is a permanent −1 with no shrinkage and no half-life. | Replace with the `(S, W, t_last)` cell from §5.1. Ten lines, and it buys cold-start shrinkage and decay at once. |
| C3 | **No provenance anywhere.** | §2 calls `source` the most important field in the schema. Without it the model learns from its own autoplay output and collapses. Currently `recordDeparture` cannot tell a user-chosen play from an autoplay. | Thread a `source` through every playback entry point. This is the single highest-value change in the whole track. |
| C4 | **Derived state is mutated in place; there is no event log.** | Nothing is reproducible or replayable. The replay harness (§14), the decision log, and offline weight tuning are all impossible against this shape. | Introduce the append-only event table as the source of truth and derive cells from it. |
| C5 | **Suppression has no expiry.** | §11's central rule is "nothing automatic is ever permanent". A suppressed song today stays suppressed forever. | 90-day expiry + the forgiveness multiplier. |
| C6 | **No content class.** | §0.2's asymmetry (Quran may be reordered, never suppressed) has nothing to key off. | Add `content_class` with a best-effort classifier; see the assumption in `DECISIONS.md`. |
| C7 | **No session layer, no context, no exploration.** | Phases 3–5. | Build in phase order. |
| C8 | `TasteEngine` is a Kotlin `object` reading `System.currentTimeMillis()` directly. | Untestable: decay, expiry and session windows cannot be exercised. | Inject a `Clock`; keep the object as a thin facade over an injectable implementation. |

## 3. Concrete schema for this storage layer

Room, `app.kreate.database.AppDatabase`, currently version 38. Three new tables, one
migration to 39. Column names snake_case to match the existing convention.

```kotlin
@Entity(tableName = "listening_events")           // append-only, never updated
data class ListeningEvent(
  @PrimaryKey val id: String,                     // ULID
  @ColumnInfo("ts")            val ts: Long,      // epoch ms
  @ColumnInfo("tz_offset_min") val tzOffsetMin: Int,
  @ColumnInfo("session_id")    val sessionId: String,
  @ColumnInfo("type")          val type: String,  // play_start, play_end, skip_next, …
  @ColumnInfo("item_id")       val itemId: String?,
  @ColumnInfo("position_ms")   val positionMs: Long?,
  @ColumnInfo("duration_ms")   val durationMs: Long?,
  @ColumnInfo("source")        val source: String,   // PROVENANCE, never null
  @ColumnInfo("slot")          val slot: Int?,
  @ColumnInfo("context")       val context: String?  // frozen JSON snapshot
)

@Entity(tableName = "affinity_cells", primaryKeys = ["facet_type","facet_value"])
data class AffinityCell(
  @ColumnInfo("facet_type")  val facetType: String,   // creator | content_class | topic | item | context
  @ColumnInfo("facet_value") val facetValue: String,
  @ColumnInfo("s")           val s: Double,           // decayed weighted evidence sum
  @ColumnInfo("w")           val w: Double,           // decayed weight sum
  @ColumnInfo("t_last")      val tLast: Long,
  @ColumnInfo("n")           val n: Int               // display only
)

@Entity(tableName = "item_facets")
data class ItemFacets(
  @PrimaryKey @ColumnInfo("item_id") val itemId: String,
  @ColumnInfo("content_class")  val contentClass: String,   // quran | nasheed | lecture | … | unknown
  @ColumnInfo("creator_id")     val creatorId: String?,
  @ColumnInfo("series_id")      val seriesId: String?,
  @ColumnInfo("language")       val language: String?,
  @ColumnInfo("duration_band")  val durationBand: String,   // derived, not stored upstream
  @ColumnInfo("topic_tags")     val topicTags: String?,     // comma-separated
  @ColumnInfo("added_at")       val addedAt: Long
)
```

`ListeningSignal` is **kept** and becomes the item-level adjustment layer of §3 rather than
the whole model, so `TasteCentreScreen` keeps working throughout the migration.

The decision log is a fourth table with a 30-day window (§13), added at P1 when there are
decisions to log.

## 4. Answers to the open questions in §16, from the code

1. **Catalogue size.** There is no fixed catalogue — this is a YouTube Music client, so the
   candidate space is effectively unbounded and *remote*. §16's premise (a small local
   catalogue) is wrong for this codebase in one direction and right in another: the
   *user's* local corpus (library, history, downloads) is small — tens to low hundreds of
   rows in the test profile. The pool guard is therefore about the **local** pool, and the
   discovery generator has to reach the network, which the architecture does not account
   for.
2. **Metadata completeness.** Poor. `Song` carries title, artist text, duration, thumbnail.
   There is **no** topic, energy, series or language field, and no content class. Every
   facet beyond `creator` has to be derived heuristically or left null. This caps the
   system exactly as §3 warns, and it is the honest reason not to over-build the ranker.
3. **Video component?** Yes — the resolver selects a video format alongside audio
   (`videoFormat`, itag 137 observed). So the car-mode "no video-primary" filter is real
   and needed.
4. **Multi-device?** A handoff feature exists (`service/handoff/`) that moves playback
   between devices by QR. There is no profile sync. Phone-first; sync stays out of P1.
5. **Who curates discovery?** Nobody. There is no curated pool and no content-safety layer.
   For an app framed as Islamic media this is a real question and I will not answer it by
   inventing a pool: the discovery generator is implemented but ships **disabled by
   default** with a note, rather than surfacing unvetted long-tail content.

## 5. Where I disagree with the architecture, for this codebase

The prompt asks for disagreement where I have it.

- **§9's 200-candidate generation and 7 generators are over-built for the data available.**
  With no topic tags, no energy and no series, four of the seven generators (facet-similar,
  session-intent-similar, discovery pool, and most of rediscovery) degenerate to
  "same creator" or "random from library". I will implement the generator *interface* and
  the three that have real inputs (same creator, favourites-not-recent, historically
  completed but cold), and leave the others as declared-but-empty rather than pretending
  they contribute. Building all seven against null facets produces confident noise, which
  is the exact failure §0.1 warns about.
- **§7's prayer-relative buckets cannot be honestly implemented here.** They need prayer
  times, which need location, which this app does not request. Implementing them from a
  sunrise/sunset approximation gives *approximately* right buckets with no way to tell the
  user how they were derived. I will build the bucket interface and the significance gate
  (which is the part that actually prevents harm) and mark the bucket source as degraded.
- **§4.1's impression events** require knowing what was displayed and not opened. This app
  has no impression instrumentation and adding it to every lazy list is a large,
  invasive change for a −0.05 signal capped at 3/session. Deferred with reason rather than
  half-built.
- **I agree with, and want to keep, the existing `userOverride` behaviour** (keep the
  counters, stop acting on them). The architecture's §6.3 prompt-once rule is good but the
  existing behaviour is a better default and should stay underneath it.

## 6. Build order (matches the master prompt's Stage 3)

- **P0** — event log with provenance; resume position; deterministic continuation; recently-played exclusion. No inference.
- **P1** — facet cells, evidence extraction, linear scorer, hard filters, diversity quotas, decision log, replay harness.
- **P2** — skip interpretation on the new model, soft suppression + 90-day expiry + forgiveness, and the full control surface, shipped together.
- **P3** — session intent, engagement, circuit breakers.
- **P4** — context buckets + significance gate (degraded source, see above).
- **P5** — exploration with directional bookkeeping.
- **P6** — not built, per the brief.
