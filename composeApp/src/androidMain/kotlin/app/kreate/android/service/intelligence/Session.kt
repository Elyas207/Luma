package app.kreate.android.service.intelligence

import kotlin.math.abs
import kotlin.math.ln

/**
 * What is happening *right now*, as distinct from what the user is generally like.
 *
 * Held in memory and never written to the model directly. The session writes events; the model is
 * derived from events. That one-way flow is what stops an unusual evening from permanently
 * rewriting a profile, and it is why every derived value can be reproduced from the log.
 */
class SessionState {

    /** EWMA over the facets of what has played, α = 0.5, so roughly a three-item memory. */
    private val intent = mutableMapOf<String, Double>()

    /** Rolling outcome over recent items, in [-1, +1]. */
    var engagement: Double = 0.0
        private set

    var consecutiveEarlySkips: Int = 0
        private set

    var autoplayDepth: Int = 0
        private set

    /** Set when the user is clearly driving the session themselves. */
    var isLeanIn: Boolean = false
        private set

    var seedWasSearch: Boolean = false
        private set

    fun seed( provenance: Provenance ) {
        seedWasSearch = provenance == Provenance.SEARCH
        isLeanIn = provenance.isUserChosen
    }

    fun observe( facetValue: String, outcome: Double, wasAutoplay: Boolean ) {
        val alpha = 0.5
        intent[facetValue] = alpha * outcome + ( 1 - alpha ) * ( intent[facetValue] ?: 0.0 )

        // Engagement over the last handful of items rather than all of them: a session that has
        // turned sour in the last four items is a sour session, however well it started.
        engagement = 0.25 * outcome + 0.75 * engagement

        if ( wasAutoplay ) autoplayDepth++ else autoplayDepth = 0

        if ( outcome < 0 ) consecutiveEarlySkips++ else consecutiveEarlySkips = 0
    }

    fun intentFor( facetValue: String ): Double = intent[facetValue] ?: 0.0

    /** Whether this session has seen enough to say anything about itself at all. */
    fun hasSignal(): Boolean = intent.values.count { it > 0 } >= 1

    /**
     * Shannon entropy over the session's facets — low means they are clearly on one thing.
     *
     * Returns 1.0 (maximum spread, i.e. "no idea") when there is nothing to measure. Returning 0.0
     * for an empty session, as this first did, is the opposite of the truth: zero entropy reads as
     * *perfect focus*, so a brand-new session claimed to be laser-focused and suppressed the
     * long-term profile it should have been leaning on entirely. A selection test caught it —
     * a strongly loved creator lost to plain continuity because the profile had been discounted to
     * 40% by an empty session.
     */
    fun intentEntropy(): Double {
        val positives = intent.values.filter { it > 0 }
        if ( positives.isEmpty() ) return 1.0
        if ( positives.size < 2 ) return 0.0
        val total = positives.sum()
        if ( total <= 0 ) return 1.0
        return -positives.sumOf { val p = it / total; p * ln( p ) } / ln( positives.size.toDouble() )
    }

    /**
     * How much to weight *this session* against the long-term profile.
     *
     * Never 1.0, and that ceiling matters: a session that fully erased the profile is how a user
     * ends up in a rut they cannot escape by pressing next, because everything they skip pushes
     * them further into the corner they are trying to leave.
     */
    fun blendWeight(): Double {
        // With no session evidence, the profile is all there is — lean on it rather than on
        // nothing. The floor for a fresh session is deliberately low, not the 0.35 default.
        if ( !hasSignal() ) return 0.15

        var beta = 0.35
        if ( intentEntropy() < 0.4 ) beta += 0.25    // clearly on one thing right now
        if ( seedWasSearch ) beta += 0.15            // they said what they wanted
        if ( engagement < 0 ) beta -= 0.20           // it is not going well; trust the profile more
        return beta.coerceIn( 0.15, 0.70 )
    }

    /** Long-term effect blended with what is happening now. */
    fun blend( longTermEffect: Double, facetValue: String ): Double {
        val beta = blendWeight()
        return ( 1 - beta ) * longTermEffect + beta * intentFor( facetValue )
    }

    // ------------------------------------------------------------------ circuit breakers

    enum class Breaker { NONE, RECOVERY, STOP_AUTOPLAY }

    /**
     * Notice quickly and get out of the way.
     *
     * Speed of recovery matters far more than initial accuracy: a system that is wrong for three
     * items and then backs off is tolerable, and one that is wrong for twenty is not, however good
     * its average is.
     */
    fun breaker(): Breaker = when {
        consecutiveEarlySkips >= 5 -> Breaker.STOP_AUTOPLAY
        consecutiveEarlySkips >= 3 -> Breaker.RECOVERY
        else -> Breaker.NONE
    }

    /** In recovery, exploration stops entirely and skip evidence is treated as mood. */
    fun explorationAllowed(): Boolean = breaker() == Breaker.NONE

    fun skipEvidenceMultiplier(): Float = if ( breaker() == Breaker.NONE ) 1f else 0.3f

    /** Two completions and we are out. */
    fun recordRecoverySuccess() {
        if ( consecutiveEarlySkips > 0 ) consecutiveEarlySkips = 0
    }
}

/**
 * When something happened, in terms that mean something to a listener.
 *
 * Prayer-relative rather than clock-relative would be the right unit for this app — "after Fajr" is
 * how someone actually thinks about their morning, and it self-adjusts through the year. That needs
 * prayer times, which need location, which this app does not ask for and which is not a permission
 * to add unprompted. So these are derived from local time with the offset the event was *recorded*
 * with, and the layer is marked degraded rather than pretending to a precision it does not have.
 */
enum class TimeBucket {
    PRE_FAJR, POST_FAJR, MORNING, MIDDAY, POST_ASR, MAGHRIB_ISHA, LATE_NIGHT;

    companion object {
        fun of( tsMillis: Long, tzOffsetMinutes: Int ): TimeBucket {
            val localMs = tsMillis + tzOffsetMinutes * 60_000L
            val hour = ( ( localMs / 3_600_000L ) % 24 ).toInt().let { if ( it < 0 ) it + 24 else it }
            return when ( hour ) {
                in 3..5   -> PRE_FAJR
                in 6..8   -> POST_FAJR
                in 9..11  -> MORNING
                in 12..14 -> MIDDAY
                in 15..17 -> POST_ASR
                in 18..21 -> MAGHRIB_ISHA
                else      -> LATE_NIGHT
            }
        }
    }
}

/**
 * Whether a context cell has earned the right to affect ranking.
 *
 * Context is where personalisation most often produces confident nonsense — one lecture at 3am
 * becoming a "3am personality". All three conditions must hold, and for most users on most contexts
 * they never will. That is the intended outcome: someone with genuinely no morning/night
 * distinction should not be handed a fabricated one.
 */
object SignificanceGate {

    const val MIN_EVENTS = 30
    const val MIN_CONFIDENCE = 0.5
    const val MIN_DIVERGENCE = 0.15

    fun passes( cell: AffinityCell, globalStrength: Double ): Boolean =
        cell.n >= MIN_EVENTS &&
        cell.confidence >= MIN_CONFIDENCE &&
        abs( cell.strength - globalStrength ) >= MIN_DIVERGENCE

    /** The context contribution, which is exactly zero unless the gate passes. */
    fun contribution( cell: AffinityCell, globalStrength: Double ): Double =
        if ( passes( cell, globalStrength ) ) cell.effect else 0.0
}

/**
 * How often to take a risk, and in which direction.
 *
 * The floor is the point. A model with no exploration calcifies, and exploration is the only way a
 * quietly wrong inference ever gets corrected without the user going looking for the control.
 */
class Exploration( private val clock: LumaClock = LumaClock.System ) {

    private data class Direction( var failures: Int = 0, var cooldownUntil: Long = 0 )

    private val directions = mutableMapOf<String, Direction>()

    companion object {
        const val BASE = 0.08
        const val FLOOR = 0.02
        const val CEILING = 0.25
        const val COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000
    }

    fun rate(
        playsSoFar: Int,
        engagement: Double,
        isCarMode: Boolean
    ): Double {
        // New users get more exploration because there is nothing to exploit yet; it tapers as
        // evidence accumulates.
        val newUserBoost = if ( playsSoFar >= 200 ) 1.0 else 2.5 - 1.5 * ( playsSoFar / 200.0 )
        val engagementFactor = when {
            engagement < 0.0 -> 0.2      // they are not enjoying this; do not experiment
            engagement > 0.6 -> 1.4      // a satisfied listener tolerates a risk
            else -> 1.0
        }
        // Driving is the worst moment to be surprising.
        val deviceFactor = if ( isCarMode ) 0.4 else 1.0

        return ( BASE * newUserBoost * engagementFactor * deviceFactor ).coerceIn( FLOOR, CEILING )
    }

    /**
     * Explore *one facet away* from something known, never at random. Random exploration in a
     * religious-content app is not charming, it is jarring.
     */
    fun isOnCooldown( direction: String, now: Long = clock.nowMillis() ): Boolean =
        ( directions[direction]?.cooldownUntil ?: 0 ) > now

    fun recordOutcome( direction: String, succeeded: Boolean, now: Long = clock.nowMillis() ) {
        val d = directions.getOrPut( direction ) { Direction() }
        if ( succeeded ) {
            d.failures = 0
            d.cooldownUntil = 0
        } else {
            d.failures++
            // Two consecutive misses in the same direction is enough to stop trying it for a while.
            if ( d.failures >= 2 ) {
                d.cooldownUntil = now + COOLDOWN_MS
                d.failures = 0
            }
        }
    }

    /** Exploration never happens after a skip, and never as the first item after a manual choice. */
    fun shouldExplore(
        lastOutcomeWasCompletion: Boolean,
        immediatelyAfterManualChoice: Boolean,
        session: SessionState,
        roll: Double,
        playsSoFar: Int,
        isCarMode: Boolean
    ): Boolean {
        if ( !lastOutcomeWasCompletion ) return false
        if ( immediatelyAfterManualChoice ) return false
        if ( !session.explorationAllowed() ) return false
        return roll < rate( playsSoFar, session.engagement, isCarMode )
    }
}
