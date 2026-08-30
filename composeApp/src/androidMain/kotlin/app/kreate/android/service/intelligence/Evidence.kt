package app.kreate.android.service.intelligence

import app.kreate.database.models.ListeningEvent

/**
 * How much an item was listened to, and how far in the user left.
 */
data class Departure(
    val completionFraction: Float,
    val positionMs: Long,
    val durationMs: Long,
    val wasSkip: Boolean
)

/**
 * A reading of one event: which direction it points, and how much that reading can be trusted.
 *
 * Keeping direction and trust as **separate numbers** is the thing that makes this robust. A skip
 * five seconds in and a skip at 45% both point negative, but one is a clear rejection and the other
 * is genuinely ambiguous — the user may have been interrupted. Collapsing them into a single score
 * forces a guess; carrying a weight lets an ambiguous event contribute almost nothing without
 * having to invent a special case for it.
 *
 * @param direction  in [-1, +1] — what the event says
 * @param weight     in [0, 1]   — how much to believe it
 */
data class Evidence( val direction: Float, val weight: Float ) {
    companion object {
        /** Says nothing. Not "says neutral" — contributes no weight at all. */
        val None = Evidence( 0f, 0f )
    }
}

/**
 * Turns events into evidence.
 *
 * Every number here is from the architecture's table and every one is a judgement call that should
 * be tuned against the replay harness rather than argued about. They are gathered in one object so
 * that tuning is a diff to one file.
 */
object EvidenceExtractor {

    /**
     * The base reading, before any modifier.
     *
     * The shape that matters: a skip in the first few seconds is the clearest ordinary rejection a
     * user ever gives, a skip near the end is mildly *positive* (they got what they came for), and
     * the middle is honestly ambiguous and is treated as such.
     */
    fun base( event: ListeningEvent ): Evidence {
        val type = event.type
        val duration = event.durationMs ?: 0L
        val position = event.positionMs ?: 0L
        val fraction = if ( duration > 0 ) ( position.toFloat() / duration ).coerceIn( 0f, 1f ) else -1f

        return when ( type ) {
            EventType.PLAY_END.wireName -> when {
                // Without a duration there is no way to distinguish a completion from an
                // abandonment, and guessing poisons the model. Contribute nothing.
                fraction < 0f    -> Evidence.None
                fraction >= 0.9f -> Evidence( +0.60f, 1.0f )
                fraction >= 0.6f -> Evidence( +0.30f, 0.8f )
                fraction >= 0.3f -> Evidence(  0.0f, 0.3f )   // genuinely ambiguous
                else             -> Evidence( -0.20f, 0.3f )
            }

            EventType.SKIP_NEXT.wireName -> when {
                fraction < 0f     -> Evidence.None
                fraction > 0.7f   -> Evidence( +0.20f, 0.5f )   // they got what they came for
                fraction >= 0.3f  -> Evidence( -0.30f, 0.6f )
                position < 2_000  -> Evidence.None              // navigation, not a judgement
                position < 5_000  -> Evidence( -0.20f, 0.25f )  // mostly mis-taps and browsing
                else              -> Evidence( -0.50f, 0.8f )   // the clearest dislike window
            }

            EventType.REPLAY.wireName        -> Evidence( +0.80f, 1.0f )
            EventType.SEARCH_RESULT_OPEN.wireName -> Evidence( +0.40f, 0.9f )
            EventType.QUEUE_ADD.wireName     -> Evidence( +0.60f, 0.9f )
            EventType.PLAYLIST_ADD.wireName  -> Evidence( +0.80f, 1.0f )
            EventType.PLAYLIST_REMOVE.wireName -> Evidence( -0.60f, 0.9f )
            EventType.QUEUE_REMOVE.wireName  -> Evidence( -0.60f, 0.9f )
            EventType.FAVOURITE.wireName     -> Evidence( +1.00f, 1.0f )
            EventType.DISLIKE.wireName       -> Evidence( -1.00f, 1.0f )
            EventType.DOWNLOAD.wireName      -> Evidence( +0.70f, 0.9f )

            // Pauses, seeks and abandons say nothing reliable about preference.
            else -> Evidence.None
        }
    }

    /**
     * Multipliers on *trust*, never on direction.
     *
     * Direction is what the user's behaviour said; trust is how much the circumstances let us
     * believe it. Keeping modifiers out of the direction means a discounted positive never becomes
     * a negative through arithmetic.
     */
    fun modified(
        event: ListeningEvent,
        evidence: Evidence = base( event ),
        isCarMode: Boolean = false,
        contentClass: ContentClass = ContentClass.UNKNOWN,
        isNewToUser: Boolean = false,
        sessionSkipStreak: Boolean = false
    ): Evidence {
        if ( evidence.weight <= 0f ) return Evidence.None

        var w = evidence.weight
        val positive = evidence.direction > 0f

        val provenance = Provenance.fromWire( event.source )

        // Passive acceptance is not endorsement. Not skipping something the app chose says much
        // less than choosing it, and without this discount the model learns from its own output.
        if ( positive && !provenance.isUserChosen ) w *= 0.7f

        if ( isCarMode ) {
            // Skipping while driving is expensive and slightly unsafe, so *not* skipping means far
            // less than it does on a phone — this is the single most over-trusted signal in car
            // listening. Conversely a skip taken anyway is deliberate, and means more.
            w *= if ( positive ) 0.4f else 1.2f
        }

        // A run of skips is a mood, not a verdict on each item in it.
        if ( sessionSkipStreak ) w *= 0.3f

        // People skip recitation for reasons that have nothing to do with preference: time
        // available, where they left off, a specific ayah they wanted. Feeding that into the same
        // negative pathway as a podcast skip is how an app starts quietly suppressing a qari the
        // user loves, which is a much worse failure than a bad recommendation.
        if ( !positive && contentClass == ContentClass.QURAN ) w *= 0.3f

        // First impressions carry information.
        if ( isNewToUser ) w *= 1.1f

        return Evidence( evidence.direction, w.coerceIn( 0f, 1f ) )
    }
}
