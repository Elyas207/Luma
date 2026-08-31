package app.kreate.android.service.intelligence

/**
 * Why an item stopped being offered, and when that stops being true.
 *
 * Three tiers, and **only one of them is permanent**:
 *
 * | Tier | Cause | Effect | How it ends |
 * |---|---|---|---|
 * | Score penalty | any negative evidence | ranked lower | automatically, on any positive |
 * | Soft suppression | repeated early skips on distinct days | not offered unprompted; still fully searchable and browsable | one tap, or expires by itself |
 * | Hard exclusion | the user said so | never surfaced | the user, and nobody else |
 *
 * That single rule — nothing automatic is ever permanent — is what handles the failure that makes
 * people distrust recommenders: the algorithm was confidently wrong and there is no way to tell it.
 * An expiry means even a user who never finds the control gets the decision undone eventually.
 */
data class SuppressionEntry(
    val itemId: String,
    /** Distinct days on which the item was skipped early. Distinct *days*, not skips. */
    val strikeDays: Set<Long>,
    val lastStrikeMillis: Long,
    /** The user said "actually, I like this". Learning stops for the item; history is kept. */
    val userOverride: Boolean = false
) {
    val strikes: Int get() = strikeDays.size
}

/**
 * The suppression register.
 *
 * Pure and injectable so the 90-day expiry can be tested with a clock jump rather than a wait.
 */
class SuppressionRegister( private val clock: LumaClock = LumaClock.System ) {

    private val entries = mutableMapOf<String, SuppressionEntry>()

    companion object {
        /** Three strikes, and they must fall on different days. */
        const val STRIKES_TO_SUPPRESS = 3

        /**
         * How long a suppression lasts without reinforcement.
         *
         * The expiry is the point. A permanent automatic decision is indistinguishable, from the
         * user's side, from the app being broken.
         */
        const val EXPIRY_MS = 90L * 24 * 60 * 60 * 1000

        /**
         * How much the *next* positive is amplified after a wrong suppression.
         *
         * Recovery from a mistaken inference should be faster than the inference was, because the
         * cost of staying wrong is borne by the user and the cost of being too forgiving is not.
         */
        const val FORGIVENESS_MULTIPLIER = 1.5f
    }

    private fun dayOf( millis: Long ): Long = millis / 86_400_000L

    /**
     * Record an early skip.
     *
     * Strikes are counted per *day*, so skipping the same track five times in one frustrated
     * evening is one strike, not five. That is the difference between "I don't like this" and
     * "not right now".
     */
    fun recordEarlySkip( itemId: String, contentClass: ContentClass, atMillis: Long = clock.nowMillis() ) {
        // Recitation may be reordered but never suppressed. Not a special case bolted on at the
        // end — it is checked before anything is recorded, so no strike can accumulate at all.
        if ( contentClass.isProtectedFromSuppression ) return

        val existing = entries[itemId]
        if ( existing?.userOverride == true ) return

        entries[itemId] = SuppressionEntry(
            itemId = itemId,
            strikeDays = ( existing?.strikeDays ?: emptySet() ) + dayOf( atMillis ),
            lastStrikeMillis = atMillis,
            userOverride = false
        )
    }

    /**
     * Any positive clears the slate for this item.
     *
     * Returns the multiplier to apply to that positive: a recovered item's next good signal counts
     * for more, so the model climbs out of a mistake faster than it fell into one.
     */
    fun recordPositive( itemId: String ): Float {
        val existing = entries.remove( itemId ) ?: return 1f
        return if ( existing.strikes > 0 ) FORGIVENESS_MULTIPLIER else 1f
    }

    /** Whether the recommender should avoid offering this unprompted, right now. */
    fun isSuppressed( itemId: String, atMillis: Long = clock.nowMillis() ): Boolean {
        val entry = entries[itemId] ?: return false
        if ( entry.userOverride ) return false
        if ( entry.strikes < STRIKES_TO_SUPPRESS ) return false
        return atMillis - entry.lastStrikeMillis < EXPIRY_MS
    }

    /** Everything currently suppressed, for the user to see and undo. */
    fun suppressedIds( atMillis: Long = clock.nowMillis() ): List<String> =
        entries.keys.filter { isSuppressed( it, atMillis ) }

    /** "Actually, I like this." Keeps the observed history; stops acting on it. */
    fun override( itemId: String ) {
        val existing = entries[itemId] ?: SuppressionEntry( itemId, emptySet(), clock.nowMillis() )
        entries[itemId] = existing.copy( userOverride = true )
    }

    fun forget( itemId: String ) { entries.remove( itemId ) }
    fun forgetAll() = entries.clear()

    fun entry( itemId: String ): SuppressionEntry? = entries[itemId]

    /**
     * Drop entries whose suppression has lapsed.
     *
     * Housekeeping only — [isSuppressed] already treats a lapsed entry as not suppressed, so
     * forgetting to call this makes the register bigger, never wrong.
     */
    fun pruneExpired( atMillis: Long = clock.nowMillis() ): Int {
        val gone = entries.filterValues {
            !it.userOverride && atMillis - it.lastStrikeMillis >= EXPIRY_MS
        }.keys
        gone.forEach( entries::remove )
        return gone.size
    }
}
