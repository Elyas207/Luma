package app.kreate.android.service.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L
private const val NOW = 1_000L * DAY

/**
 * The selector, judged on what it *chooses* rather than on which functions it calls.
 *
 * These cover the failure catalogue's selection cases: dead air on a thin library, one creator
 * playing forever, an inferred dislike making something unreachable, and recitation being treated
 * like everything else.
 */
class SelectionTest {

    private fun selector(
        model: AffinityModel = AffinityModel(),
        suppression: SuppressionRegister = SuppressionRegister( TestClock( NOW ) ),
        session: SessionState = SessionState()
    ) = Selector( model, suppression, session )

    private fun candidate(
        id: String,
        creator: String = "c",
        cls: ContentClass = ContentClass.NASHEED,
        source: Generator = Generator.LIBRARY,
        lastPlayed: Long? = null,
        recentPlays: Int = 0,
        favourite: Boolean = false,
        disliked: Boolean = false
    ) = Candidate(
        itemId = id, creator = creator, contentClass = cls, source = source,
        recentPlays = recentPlays, lastPlayedMillis = lastPlayed,
        isFavourite = favourite, isDisliked = disliked
    )

    // ------------------------------------------------------------------ filters

    @Test
    fun `an explicit dislike is never chosen`() {
        val d = selector().choose(
            listOf( candidate( "bad", disliked = true ) ), NOW
        )
        assertEquals( null, d.chosen )
    }

    @Test
    fun `a suppressed item is not offered unprompted`() {
        val clock = TestClock( NOW )
        val reg = SuppressionRegister( clock )
        repeat( 3 ) { reg.recordEarlySkip( "x", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }

        val d = selector( suppression = reg ).choose(
            listOf( candidate( "x" ), candidate( "y" ) ), clock.nowMillis()
        )
        assertEquals( "y", d.chosen?.candidate?.itemId )
    }

    @Test
    fun `recitation can repeat after two hours where a nasheed cannot repeat for a week`() {
        // Deliberate repetition of recitation is normal; a week-long window would refuse to replay
        // a surah someone is working through.
        val threeHoursAgo = NOW - 3 * 3_600_000L

        val quran = selector().choose(
            listOf( candidate( "q", cls = ContentClass.QURAN, lastPlayed = threeHoursAgo ) ), NOW
        )
        assertNotNull( "recitation should be available again after 3 hours", quran.chosen )

        val nasheed = selector().choose(
            listOf( candidate( "n", cls = ContentClass.NASHEED, lastPlayed = threeHoursAgo ) ), NOW
        )
        // Only one candidate, so the pool guard relaxes rather than dead-air — but it must have
        // had to fire, which is the point.
        assertTrue( "a nasheed 3 hours old should not pass the normal window", nasheed.poolGuardFired )
    }

    // ------------------------------------------------------------------ pool guard

    @Test
    fun `a thin library relaxes recency rather than producing silence`() {
        val recent = NOW - 1 * DAY
        val thin = ( 1..3 ).map { candidate( "n$it", lastPlayed = recent ) }

        val d = selector().choose( thin, NOW )

        assertNotNull( "dead air is never the right answer", d.chosen )
        assertTrue( "and it should say the catalogue was the constraint", d.poolGuardFired )
    }

    @Test
    fun `the pool guard does not fire when there is plenty to choose from`() {
        val plenty = ( 1..30 ).map { candidate( "n$it" ) }
        val d = selector().choose( plenty, NOW )
        assertTrue( !d.poolGuardFired )
        assertNotNull( d.chosen )
    }

    @Test
    fun `even the last resort refuses an explicit dislike`() {
        // Everything filtered, so the guard falls through to "anything not rejected" — and a
        // dislike must survive that, or the user's only hard control is not hard.
        val all = listOf(
            candidate( "a", lastPlayed = NOW - 1000, disliked = true ),
            candidate( "b", lastPlayed = NOW - 1000, disliked = true )
        )
        assertEquals( null, selector().choose( all, NOW ).chosen )
    }

    // ------------------------------------------------------------------ scoring

    @Test
    fun `continuity beats a mild preference for someone else`() {
        val model = AffinityModel()
        // A modest liking for "other", built from a couple of completions.
        repeat( 2 ) { model.observe( FacetType.CREATOR, "other", Evidence( 0.6f, 1f ), NOW ) }

        val d = selector( model ).choose(
            listOf( candidate( "same", creator = "current" ), candidate( "diff", creator = "other" ) ),
            NOW,
            justPlayedCreator = "current"
        )
        assertEquals( "more of what is playing should win", "same", d.chosen?.candidate?.itemId )
    }

    @Test
    fun `continuity outranks even a maximal learned preference, by design`() {
        // Worth stating explicitly because it looks like a bug the first time you see it. Affinity
        // is bounded by confidence, which never reaches 1, so its effect tops out around 0.83
        // against continuity's flat 0.85. That ordering is deliberate: nothing a ranker produces
        // beats "more of what is already playing", and a system that jumps away from the thing you
        // are enjoying to serve its favourite is the most noticeable failure a listener can have.
        val model = AffinityModel()
        repeat( 40 ) { model.observe( FacetType.CREATOR, "loved", Evidence( 1.0f, 1f ), NOW ) }

        val d = selector( model ).choose(
            listOf( candidate( "same", creator = "current" ), candidate( "loved", creator = "loved" ) ),
            NOW,
            justPlayedCreator = "current"
        )
        assertEquals( "same", d.chosen?.candidate?.itemId )
    }

    @Test
    fun `affinity decides once continuity is out of the picture`() {
        // With nothing currently playing, the profile is what is left to go on.
        val model = AffinityModel()
        repeat( 40 ) { model.observe( FacetType.CREATOR, "loved", Evidence( 1.0f, 1f ), NOW ) }
        repeat( 6 ) { model.observe( FacetType.CREATOR, "meh", Evidence( -0.4f, 1f ), NOW ) }

        val d = selector( model ).choose(
            listOf( candidate( "a", creator = "meh" ), candidate( "b", creator = "loved" ) ),
            NOW,
            justPlayedCreator = null
        )
        assertEquals( "b", d.chosen?.candidate?.itemId )
    }

    @Test
    fun `a fresh session leans on the profile rather than on nothing`() {
        // An empty session has no evidence, and an earlier version treated that as *perfect focus*
        // — zero entropy — which discounted the long-term profile to 40% for a user the app knew
        // plenty about. A selection test caught it.
        val fresh = SessionState()
        assertTrue( "an empty session must not claim focus", fresh.blendWeight() <= 0.2 )

        val engaged = SessionState().apply {
            seed( Provenance.SEARCH )
            repeat( 3 ) { observe( "one-thing", 1.0, wasAutoplay = false ) }
        }
        assertTrue( "a focused session should assert itself", engaged.blendWeight() > fresh.blendWeight() )
    }

    @Test
    fun `something played repeatedly is downranked against an equal alternative`() {
        val d = selector().choose(
            listOf(
                candidate( "worn", recentPlays = 12, lastPlayed = NOW - 40 * DAY ),
                candidate( "fresh" )
            ),
            NOW
        )
        assertEquals( "fresh", d.chosen?.candidate?.itemId )
    }

    @Test
    fun `an explicit favourite outranks an equal non-favourite`() {
        val d = selector().choose(
            listOf( candidate( "plain" ), candidate( "loved", favourite = true ) ), NOW
        )
        assertEquals( "loved", d.chosen?.candidate?.itemId )
    }

    // ------------------------------------------------------------------ diversity

    @Test
    fun `three in a row from one creator forces a change`() {
        // A ranker left alone will play one voice for an hour, and that reads as the app being
        // stuck rather than confident.
        val d = selector().choose(
            listOf( candidate( "a4", creator = "same" ), candidate( "b1", creator = "other" ) ),
            NOW,
            lastCreators = listOf( "same", "same", "same" )
        )
        assertEquals( "other", d.chosen?.candidate?.creator )
    }

    @Test
    fun `the quota yields rather than dead-air when nothing else exists`() {
        val d = selector().choose(
            listOf( candidate( "a4", creator = "same" ) ),
            NOW,
            lastCreators = listOf( "same", "same", "same" )
        )
        assertNotNull( "silence is worse than a fourth in a row", d.chosen )
    }

    @Test
    fun `two creators alternating do not trip the quota`() {
        val d = selector().choose(
            listOf( candidate( "x", creator = "same" ) ),
            NOW,
            lastCreators = listOf( "same", "other", "same" )
        )
        assertEquals( "same", d.chosen?.candidate?.creator )
    }

    // ------------------------------------------------------------------ explainability

    @Test
    fun `every decision can be rendered as a sentence`() {
        val d = selector().choose( listOf( candidate( "a", favourite = true ) ), NOW, justPlayedCreator = "c" )
        val why = d.chosen!!.explain()

        assertTrue( "should name its terms, got: $why", why.contains( "continuity" ) )
        assertTrue( why.contains( "favourite" ) )
    }

    @Test
    fun `the decision records what it considered and what survived`() {
        val candidates = ( 1..20 ).map { candidate( "n$it" ) } +
                         candidate( "no", disliked = true )
        val d = selector().choose( candidates, NOW )

        assertEquals( 21, d.consideredCount )
        assertEquals( 20, d.filteredCount )
        assertTrue( d.topAlternatives.isNotEmpty() )
    }

    @Test
    fun `the same inputs always produce the same decision`() {
        val candidates = ( 1..25 ).map { candidate( "n$it", creator = "c${it % 4}" ) }
        val a = selector().choose( candidates, NOW, justPlayedCreator = "c1" )
        val b = selector().choose( candidates, NOW, justPlayedCreator = "c1" )

        assertEquals( a.chosen?.candidate?.itemId, b.chosen?.candidate?.itemId )
        assertEquals( a.chosen!!.score, b.chosen!!.score, 1e-12 )
    }

    @Test
    fun `an empty catalogue returns nothing rather than throwing`() {
        val d = selector().choose( emptyList(), NOW )
        assertEquals( null, d.chosen )
        assertEquals( 0, d.consideredCount )
    }
}
