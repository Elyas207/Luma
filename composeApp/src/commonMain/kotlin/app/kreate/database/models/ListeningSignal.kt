package app.kreate.database.models

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * What the app has observed about one song.
 *
 * Deliberately *counters plus a derived score*, not an opaque embedding. Every number here can be
 * shown to the user in a sentence — "skipped quickly 4 times, never finished" — which is what makes
 * the personalisation explainable and therefore undoable. A model whose reasoning cannot be
 * rendered as plain text has no business quietly changing what someone hears.
 *
 * Signals are only ever used to *reorder what is offered*. Nothing here can make a song
 * unreachable: search and the library always return everything.
 */
@Immutable
@Entity(tableName = "listening_signals")
data class ListeningSignal(

    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,

    /** Played to (near) the end. */
    @ColumnInfo(name = "completions", defaultValue = "0")
    val completions: Int = 0,

    /**
     * Skipped after hearing a meaningful part of it. Weak negative — often just "not right now"
     * rather than dislike.
     */
    @ColumnInfo(name = "late_skips", defaultValue = "0")
    val lateSkips: Int = 0,

    /**
     * Skipped within the first few seconds. The strongest ordinary rejection signal a user gives:
     * they recognised it and did not want it.
     */
    @ColumnInfo(name = "fast_skips", defaultValue = "0")
    val fastSkips: Int = 0,

    /** Played again shortly after finishing. The clearest positive signal there is. */
    @ColumnInfo(name = "replays", defaultValue = "0")
    val replays: Int = 0,

    /** Explicitly removed from the queue — deliberate, and weighted accordingly. */
    @ColumnInfo(name = "removals", defaultValue = "0")
    val removals: Int = 0,

    /** Set when the user overrides the app: "I actually like this". Learning stops for this song. */
    @ColumnInfo(name = "user_override", defaultValue = "0")
    val userOverride: Boolean = false,

    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0
) {

    /**
     * Affinity in roughly -1..+1.
     *
     * Weights encode intent, not arithmetic convenience: a queue removal is deliberate and worth
     * more than a skip; a fast skip is worth far more than a late one; a replay outweighs several
     * completions because it is rare and unambiguous.
     */
    val score: Float
        get() {
            val raw =
                completions * 0.5f +
                replays * 1.5f -
                lateSkips * 0.2f -
                fastSkips * 1.0f -
                removals * 1.5f

            // Squash so a long history cannot run away to an extreme that is impossible to undo.
            return ( raw / 5f ).coerceIn( -1f, 1f )
        }

    /** Total times this song has been started. */
    val encounters: Int
        get() = completions + lateSkips + fastSkips

    /**
     * Whether the app should stop offering this unprompted.
     *
     * Requires *three* independent strong rejections, never one. Acting on a single skip would make
     * the app feel by turns psychic and broken, and would be impossible for the user to reason
     * about. A user override disables this permanently for the song.
     */
    val isSuppressed: Boolean
        get() = !userOverride && ( fastSkips + removals ) >= 3 && score <= -0.4f

    /** Content the app is confident about in the positive direction. */
    val isLoved: Boolean
        get() = replays >= 2 || ( completions >= 4 && score >= 0.5f )

    /**
     * A sentence explaining the current state, shown verbatim in the control centre.
     * If this cannot be written, the app should not be acting on the signal.
     */
    val explanation: String
        get() = when {
            userOverride -> "You told the app you like this"
            isSuppressed && removals > 0 ->
                "Removed from your queue $removals ${plural( removals, "time", "times" )}"
            isSuppressed ->
                "Skipped straight away $fastSkips ${plural( fastSkips, "time", "times" )}"
            isLoved && replays >= 2 ->
                "Replayed $replays ${plural( replays, "time", "times" )}"
            isLoved -> "Finished $completions ${plural( completions, "time", "times" )}"
            else -> "Played $encounters ${plural( encounters, "time", "times" )}"
        }

    private fun plural( n: Int, one: String, many: String ) = if ( n == 1 ) one else many
}
