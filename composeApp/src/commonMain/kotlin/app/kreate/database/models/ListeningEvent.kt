package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One thing that happened, recorded once and never rewritten.
 *
 * This table is **append-only**, and that is the whole point of it. Everything the personalisation
 * layer believes is *derived* from these rows, so any derived value can be recomputed, replayed
 * against different weights, diffed, or thrown away and rebuilt. The alternative — mutating a score
 * in place as events arrive, which is what this app did before — cannot be replayed, cannot be
 * debugged after the fact, and cannot be tuned offline, because the evidence is destroyed as it is
 * consumed.
 *
 * Distinct from [Event] (`playback_history`), which is the user-facing "what did I listen to" list.
 * That one answers a question the user asks; this one answers questions the ranker asks.
 *
 * ### `source` is the most important column here
 *
 * It records *why* something played: the user chose it, or the app did. Without it the model learns
 * from its own output — autoplay picks a track, the user does not skip it, the model reads that as
 * endorsement and picks it more often. Within weeks the profile collapses onto whatever the ranker
 * happened to like early, and every measurement of "the user likes X" is really "the ranker
 * suggested X". There is no way to recover from that after the fact, because the events do not
 * record which was which. It is cheap to store and impossible to reconstruct later.
 *
 * ### What is deliberately not here
 *
 * No location, no cross-app signal, no contact data, no derived "mood". Every column has to survive
 * the question: does this measurably improve a recommendation, *and* would it be comfortable to
 * show the user in plain language?
 */
@Immutable
@Entity(
    tableName = "listening_events",
    indices = [
        Index( value = ["ts"] ),
        Index( value = ["item_id"] ),
        Index( value = ["session_id"] )
    ]
)
data class ListeningEvent(

    /**
     * ULID: sorts lexicographically by creation time, so the log reads in order without relying on
     * a clock that can move backwards (see [tzOffsetMinutes]).
     */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Epoch millis. */
    @ColumnInfo(name = "ts")
    val ts: Long,

    /**
     * Stored separately from [ts] rather than folded into it.
     *
     * Time-of-day buckets are computed from local time, so a user who flies to another timezone —
     * or a device whose clock is corrected — would otherwise silently reassign every past event to
     * a different bucket. Keeping the offset the event was *recorded* with makes the bucketing
     * reproducible after the fact.
     */
    @ColumnInfo(name = "tz_offset_min", defaultValue = "0")
    val tzOffsetMinutes: Int,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    /** See `EventType`. Stored as a string so an unknown future type never crashes an old build. */
    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "item_id")
    val itemId: String? = null,

    /** Where playback had reached, for the events where that means something. */
    @ColumnInfo(name = "position_ms")
    val positionMs: Long? = null,

    /**
     * The item's length *at the time of the event*.
     *
     * Recorded per event rather than looked up later because a completion percentage computed
     * against a duration that has since changed is silently wrong, and there is no way to detect it.
     */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long? = null,

    /** Provenance. Never null — an event that cannot say where it came from is not worth storing. */
    @ColumnInfo(name = "source")
    val source: String,

    /** Position in the queue or recommendation list this came from, when there was one. */
    @ColumnInfo(name = "slot")
    val slot: Int? = null,

    /** Frozen context snapshot, JSON. Frozen because context computed later is a different fact. */
    @ColumnInfo(name = "context")
    val context: String? = null
)
