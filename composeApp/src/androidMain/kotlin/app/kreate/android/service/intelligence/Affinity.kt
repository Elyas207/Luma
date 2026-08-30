package app.kreate.android.service.intelligence

import kotlin.math.abs
import kotlin.math.pow

/**
 * What kind of thing an item is.
 *
 * A first-class dimension rather than a tag, because one rule in this system depends on it
 * absolutely: recitation can be reordered but must never be suppressed by inference.
 *
 * `UNKNOWN` is the default and is deliberately not treated as any of the others. This codebase is a
 * general music client with no content metadata, so most items genuinely are unknown, and inventing
 * a class for them would make the Quran protection fire on the wrong things — or, worse, fail to
 * fire on the right ones.
 */
enum class ContentClass {
    QURAN, DUA_DHIKR, NASHEED, LECTURE, PODCAST, SHORT, MUSIC, UNKNOWN;

    /** Inference may reorder these but may never hide them. */
    val isProtectedFromSuppression: Boolean get() = this == QURAN
}

/** Which axis an affinity is measured along. */
enum class FacetType( val informativeness: Float, val shrinkage: Float, val halfLifeDays: Double ) {
    /** Who made it. By far the most predictive facet in a media app. */
    CREATOR( 1.0f, 8f, 90.0 ),
    /** What kind of thing it is. Nearly deterministic once known. */
    CONTENT_CLASS( 0.9f, 5f, 90.0 ),
    /** Noisy, and in this codebase usually absent. */
    TOPIC( 0.5f, 15f, 45.0 ),
    /** Context-dependent: a 40-minute item is right at night and wrong on a commute. */
    DURATION_BAND( 0.3f, 12f, 60.0 ),
    /** Only meaningful once an item has real history; mostly used for repetition control. */
    ITEM( 0.8f, 4f, 30.0 ),
    /** Time-of-day and similar. Shrunk hard on purpose — see the significance gate. */
    CONTEXT( 0.4f, 25f, 60.0 )
}

/**
 * One learned preference.
 *
 * Stores decayed sums rather than an average, which buys three properties for about ten lines:
 *
 * - **Cold start is handled without a special case.** One event gives `w ≈ 1`, and with a shrinkage
 *   of 8 that is a confidence of about 0.11 — so a single strong action moves ranking by roughly a
 *   tenth of what it literally said. "One action should rarely define a preference" becomes
 *   arithmetic instead of a rule someone has to remember to apply.
 * - **Decay needs no scheduled job.** It happens on read and write via the half-life factor.
 * - **Contradiction reads honestly.** Someone who loves a creator but skipped three tracks ends up
 *   with a moderate strength and a *high* confidence, which is exactly the truth of the situation.
 */
data class AffinityCell(
    val facetType: FacetType,
    val facetValue: String,
    /** Decayed weighted sum of evidence direction. */
    val s: Double = 0.0,
    /** Decayed sum of weights. */
    val w: Double = 0.0,
    val tLast: Long = 0L,
    /** Raw count, for display only — never used in ranking. */
    val n: Int = 0
) {

    /** What the evidence says, in [-1, +1]. */
    val strength: Double get() = if ( w <= 1e-9 ) 0.0 else ( s / w ).coerceIn( -1.0, 1.0 )

    /** How much evidence there is, in [0, 1). Never reaches 1 — certainty is not available. */
    val confidence: Double get() = w / ( w + facetType.shrinkage )

    /** What ranking actually uses. A confident weak preference outranks a loud guess. */
    val effect: Double get() = strength * confidence

    /**
     * Fold in a new reading.
     *
     * Decay is applied to the *existing* sums before adding, so the result depends only on the
     * elapsed time between events and never on when this happened to be computed. That is what
     * makes replaying the same log twice produce the same answer.
     */
    fun update( evidence: Evidence, atMillis: Long ): AffinityCell {
        if ( evidence.weight <= 0f ) return this

        val lambda = decayFactor( atMillis )
        return copy(
            s = s * lambda + evidence.direction * evidence.weight,
            w = w * lambda + evidence.weight,
            tLast = maxOf( atMillis, tLast ),
            n = n + 1
        )
    }

    /** The value as of [atMillis], without recording anything. */
    fun decayedTo( atMillis: Long ): AffinityCell {
        val lambda = decayFactor( atMillis )
        return copy( s = s * lambda, w = w * lambda )
    }

    private fun decayFactor( atMillis: Long ): Double {
        // Emptiness is "no evidence yet", not "tLast is zero" — zero is a perfectly legitimate
        // timestamp, and using it as a sentinel meant a cell created at epoch never decayed. The
        // half-life test caught it.
        if ( w <= 1e-9 ) return 1.0
        // A backwards clock must not *amplify* old evidence, which is what a negative elapsed time
        // would do through the exponent. Clamp rather than trust the clock.
        val elapsedDays = ( ( atMillis - tLast ).coerceAtLeast( 0 ) ).toDouble() / 86_400_000.0
        return 0.5.pow( elapsedDays / facetType.halfLifeDays )
    }
}

/**
 * The whole learned profile: every cell, keyed by facet.
 *
 * Pure and in-memory on purpose. The same class runs in the app and in the replay harness, so a
 * scenario in a test exercises the identical arithmetic the device does — if these diverged, the
 * harness would be measuring something no user ever experiences.
 */
class AffinityModel {

    private val cells = mutableMapOf<Pair<FacetType, String>, AffinityCell>()

    fun cell( type: FacetType, value: String ): AffinityCell =
        cells[type to value] ?: AffinityCell( type, value )

    fun observe( type: FacetType, value: String, evidence: Evidence, atMillis: Long ) {
        if ( value.isBlank() || evidence.weight <= 0f ) return
        // Facets are not equally informative, so a completion teaches you a lot about the creator
        // and very little about the duration band it happened to fall in.
        val scaled = Evidence( evidence.direction, evidence.weight * type.informativeness )
        cells[type to value] = cell( type, value ).update( scaled, atMillis )
    }

    /** Effect of one facet as of a moment in time. */
    fun effect( type: FacetType, value: String, atMillis: Long ): Double =
        cell( type, value ).decayedTo( atMillis ).effect

    fun all(): List<AffinityCell> = cells.values.toList()

    fun forget( type: FacetType, value: String ) { cells.remove( type to value ) }
    fun forgetType( type: FacetType ) { cells.keys.filter { it.first == type }.forEach( cells::remove ) }
    fun forgetAll() = cells.clear()

    /**
     * Has this preference moved recently, enough to be worth adapting faster?
     *
     * Decay alone is too slow when something real changes — a new job, Ramadan, a genuine shift in
     * taste. This looks for a *sign flip* backed by enough recent evidence, rather than reacting to
     * every wobble.
     */
    fun hasShifted( recent: AffinityCell, trailing: AffinityCell ): Boolean =
        recent.w >= 10.0 &&
        abs( recent.strength ) > 0.1 &&
        abs( trailing.strength ) > 0.1 &&
        ( recent.strength > 0 ) != ( trailing.strength > 0 )
}
