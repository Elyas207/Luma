package app.kreate.android.service.intelligence

import kotlin.math.ln
import kotlin.math.max

/**
 * Choosing what plays next.
 *
 * Deliberately a **linear** scorer. A gradient-boosted model would score perhaps a few percent
 * better on a dataset this app does not have, and it would make every decision unexplainable — so
 * "why this?" could not be answered, the offline harness could not diff two weight sets, and a bad
 * recommendation could not be traced to the term that caused it. Every weight lives in one object
 * and every decision is emitted as a sorted list of contributions.
 */

/** A thing that could play next, and where it came from. */
data class Candidate(
    val itemId: String,
    val creator: String,
    val contentClass: ContentClass = ContentClass.UNKNOWN,
    val source: Generator,
    /** Plays in the last 30 days, for the repetition penalty. */
    val recentPlays: Int = 0,
    /** When it was last played, or null if never. */
    val lastPlayedMillis: Long? = null,
    val addedAtMillis: Long = 0L,
    val isFavourite: Boolean = false,
    val isDisliked: Boolean = false,
    val durationMs: Long = 0L
)

/**
 * Where a candidate came from.
 *
 * Retained through scoring because it drives the diversity quotas and because a decision that
 * cannot say *why* a candidate was in the running is not explainable. Only the generators with real
 * inputs in this codebase are listed — see `docs/audit/02-intelligence-plan.md` §5 for why the
 * architecture's facet-similarity and discovery generators are not among them: with no topic tags,
 * no energy and no series data they would degenerate to "same creator" or "random", which is
 * confident noise wearing the costume of a recommendation.
 */
enum class Generator( val cap: Int, val risk: Double ) {
    /** More from whoever is playing. The single most reliable signal in a media app. */
    SAME_CREATOR( 40, 0.0 ),
    /** Explicitly loved, not heard recently. The dependable fallback pool. */
    FAVOURITE( 25, 0.0 ),
    /** Finished before, cold for a while. Cheap, and it lands more often than novelty does. */
    REDISCOVERY( 20, 0.1 ),
    /** Everything else in the local library. */
    LIBRARY( 40, 0.2 )
}

/** The weights, in one place, so tuning is a diff to one object. */
object ScoringWeights {
    const val AFFINITY = 1.00
    const val CONTINUITY = 0.85
    const val CONTEXT_FIT = 0.45
    const val REDISCOVERY = 0.20
    const val NOVELTY = 0.15
    const val FRESHNESS = 0.10
    const val REPETITION_PENALTY = -0.60
    const val SUPPRESSION_PENALTY = -0.90
    const val GENERATOR_RISK = -0.40
}

/** One term of a decision, kept so the whole thing can be rendered as a sentence. */
data class Contribution( val term: String, val value: Double )

data class ScoredCandidate(
    val candidate: Candidate,
    val score: Double,
    val contributions: List<Contribution>
) {
    /** Why this, in descending order of what actually moved the number. */
    fun explain(): String =
        contributions.filter { kotlin.math.abs( it.value ) > 0.001 }
                     .sortedByDescending { kotlin.math.abs( it.value ) }
                     .joinToString( ", " ) { "${it.term} ${"%+.2f".format( it.value )}" }
}

/** What was decided, and everything needed to reconstruct why. */
data class Decision(
    val chosen: ScoredCandidate?,
    val consideredCount: Int,
    val filteredCount: Int,
    val poolGuardFired: Boolean,
    val topAlternatives: List<ScoredCandidate>
)

/**
 * The pipeline: filter, score, diversify, choose.
 *
 * Pure — no database, no player, no clock of its own. Everything it needs is passed in, so the
 * replay harness exercises exactly the code the app runs.
 */
class Selector(
    private val model: AffinityModel,
    private val suppression: SuppressionRegister,
    private val session: SessionState = SessionState()
) {

    companion object {
        /** Below this many survivors, recency windows relax rather than let the queue dead-air. */
        const val POOL_GUARD_MIN = 15

        /** Class-dependent recency windows, in milliseconds. */
        val RECENCY: Map<ContentClass, Long> = mapOf(
            ContentClass.NASHEED to 7L * 86_400_000,
            ContentClass.LECTURE to 30L * 86_400_000,
            ContentClass.PODCAST to 30L * 86_400_000,
            ContentClass.SHORT to 3L * 86_400_000,
            // Two hours only. Repeating recitation deliberately is normal and healthy, and a
            // seven-day window here would quietly refuse to replay a surah someone is memorising.
            ContentClass.QURAN to 2L * 3_600_000,
            ContentClass.MUSIC to 7L * 86_400_000,
            ContentClass.DUA_DHIKR to 1L * 86_400_000,
            ContentClass.UNKNOWN to 7L * 86_400_000
        )

        const val MAX_CONSECUTIVE_SAME_CREATOR = 3
    }

    /**
     * Hard filters. These *exclude*; they never merely downrank.
     *
     * Inference is not allowed in here — only explicit user statements and mechanical rules. That
     * separation is the whole point: an inferred dislike can lower a score, but it can never make
     * something unreachable, because an inference the user cannot overrule is indistinguishable
     * from a bug.
     */
    private fun survivesFilters(
        candidate: Candidate,
        nowMillis: Long,
        recentlyPlayedIds: Set<String>,
        relaxFactor: Double
    ): Boolean {
        if ( candidate.isDisliked ) return false
        if ( suppression.isSuppressed( candidate.itemId, nowMillis ) ) return false
        if ( candidate.itemId in recentlyPlayedIds ) return false

        val window = ( RECENCY[candidate.contentClass] ?: RECENCY.getValue( ContentClass.UNKNOWN ) )
        val relaxed = ( window * relaxFactor ).toLong()
        val last = candidate.lastPlayedMillis
        if ( last != null && nowMillis - last < relaxed ) return false

        return true
    }

    fun score(
        candidate: Candidate,
        nowMillis: Long,
        justPlayedCreator: String?,
        contextContribution: Double = 0.0
    ): ScoredCandidate {
        val contributions = mutableListOf<Contribution>()

        // What the profile thinks of this creator, blended with what this session is about.
        val longTerm = model.effect( FacetType.CREATOR, candidate.creator, nowMillis )
        val blended = session.blend( longTerm, candidate.creator )
        contributions += Contribution( "affinity", ScoringWeights.AFFINITY * blended )

        // Nothing a ranker produces beats "more of what is already playing".
        val continuity = if ( justPlayedCreator != null && candidate.creator == justPlayedCreator ) 1.0 else 0.0
        contributions += Contribution( "continuity", ScoringWeights.CONTINUITY * continuity )

        // Usually exactly zero — see SignificanceGate.
        contributions += Contribution( "context", ScoringWeights.CONTEXT_FIT * contextContribution )

        // Loved once, cold for a while. High hit rate and it costs nothing to look.
        val coldDays = candidate.lastPlayedMillis?.let { ( nowMillis - it ) / 86_400_000.0 } ?: 0.0
        val rediscovery = if ( candidate.recentPlays > 0 && coldDays > 60 ) 1.0 else 0.0
        contributions += Contribution( "rediscovery", ScoringWeights.REDISCOVERY * rediscovery )

        val novelty = if ( candidate.lastPlayedMillis == null ) 1.0 else 0.0
        contributions += Contribution( "novelty", ScoringWeights.NOVELTY * novelty )

        val ageDays = ( nowMillis - candidate.addedAtMillis ) / 86_400_000.0
        val freshness = if ( candidate.addedAtMillis > 0 && ageDays < 30 ) 1.0 - ( ageDays / 30.0 ) else 0.0
        contributions += Contribution( "freshness", ScoringWeights.FRESHNESS * freshness )

        // Log-scaled: the difference between one play and three matters far more than between
        // twenty and twenty-two.
        val repetition = if ( candidate.recentPlays > 0 ) ln( 1.0 + candidate.recentPlays ) else 0.0
        contributions += Contribution( "repetition", ScoringWeights.REPETITION_PENALTY * repetition )

        contributions += Contribution( "generator risk", ScoringWeights.GENERATOR_RISK * candidate.source.risk )

        // An explicit favourite outranks anything inference has to say about it.
        if ( candidate.isFavourite ) contributions += Contribution( "favourite", 0.5 )

        return ScoredCandidate( candidate, contributions.sumOf { it.value }, contributions )
    }

    /**
     * Filter, score, diversify, choose.
     *
     * Deterministic: given the same inputs it returns the same decision, which is what lets the
     * harness diff two weight sets and attribute the difference to the weights.
     */
    fun choose(
        candidates: List<Candidate>,
        nowMillis: Long,
        justPlayedCreator: String? = null,
        recentlyPlayedIds: Set<String> = emptySet(),
        lastCreators: List<String> = emptyList()
    ): Decision {
        if ( candidates.isEmpty() )
            return Decision( null, 0, 0, poolGuardFired = false, topAlternatives = emptyList() )

        var relax = 1.0
        var survivors = candidates.filter { survivesFilters( it, nowMillis, recentlyPlayedIds, relax ) }
        var guardFired = false

        // A thin library must never cause dead air. If it does, that is the catalogue talking and
        // not the algorithm, so relax the recency windows rather than return nothing.
        if ( survivors.size < POOL_GUARD_MIN && survivors.size < candidates.size ) {
            guardFired = true
            relax = 0.5
            survivors = candidates.filter { survivesFilters( it, nowMillis, recentlyPlayedIds, relax ) }
            if ( survivors.isEmpty() )
                // Last resort: anything the user has not explicitly rejected. Silence is worse.
                survivors = candidates.filterNot { it.isDisliked }
        }

        if ( survivors.isEmpty() )
            return Decision( null, candidates.size, 0, guardFired, emptyList() )

        val scored = survivors.map { score( it, nowMillis, justPlayedCreator ) }
                              .sortedByDescending { it.score }

        // Quota: never more than three in a row from one creator, however much the profile likes
        // them. A ranker left alone will happily play the same voice for an hour, and the user
        // experiences that as the app being stuck rather than as it being confident.
        val streak = lastCreators.takeLast( MAX_CONSECUTIVE_SAME_CREATOR )
        val creatorBlocked = streak.size >= MAX_CONSECUTIVE_SAME_CREATOR && streak.distinct().size == 1
        val eligible =
            if ( creatorBlocked ) scored.filterNot { it.candidate.creator == streak.first() }.ifEmpty { scored }
            else scored

        return Decision(
            chosen = eligible.firstOrNull(),
            consideredCount = candidates.size,
            filteredCount = survivors.size,
            poolGuardFired = guardFired,
            topAlternatives = eligible.take( 5 )
        )
    }
}
