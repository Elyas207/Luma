package app.kreate.android.service.intelligence

import app.kreate.database.models.ListeningEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L

class AffinityTest {

    private fun event(
        type: EventType,
        source: Provenance = Provenance.MANUAL_BROWSE,
        positionMs: Long? = null,
        durationMs: Long? = null,
        ts: Long = 0
    ) = ListeningEvent(
        id = "x", ts = ts, tzOffsetMinutes = 0, sessionId = "s",
        type = type.wireName, itemId = "i", positionMs = positionMs,
        durationMs = durationMs, source = source.wireName
    )

    // ------------------------------------------------------------------ cold start

    @Test
    fun `one strong action moves ranking by about a tenth of what it says`() {
        val cell = AffinityCell( FacetType.CREATOR, "qari" )
            .update( Evidence( 1.0f, 1.0f ), 0 )

        // Strength is what the action said; effect is what ranking gets.
        assertEquals( 1.0, cell.strength, 1e-6 )
        assertTrue( "confidence after one event should be small, was ${cell.confidence}",
            cell.confidence < 0.15 )
        assertTrue( "a single action must not dominate, effect was ${cell.effect}",
            cell.effect < 0.15 )
    }

    @Test
    fun `confidence grows with evidence but never reaches certainty`() {
        var cell = AffinityCell( FacetType.CREATOR, "qari" )
        repeat( 200 ) { cell = cell.update( Evidence( 1.0f, 1.0f ), it * 1000L ) }
        assertTrue( cell.confidence > 0.9 )
        assertTrue( "confidence must stay below 1", cell.confidence < 1.0 )
    }

    // ------------------------------------------------------------------ decay

    @Test
    fun `evidence halves after exactly one half-life`() {
        val cell = AffinityCell( FacetType.CREATOR, "qari" ).update( Evidence( 1.0f, 1.0f ), 0 )
        val later = cell.decayedTo( ( FacetType.CREATOR.halfLifeDays * DAY ).toLong() )
        assertEquals( cell.w / 2, later.w, 1e-6 )
    }

    @Test
    fun `decay depends on elapsed time, not on when it is computed`() {
        // Same log, evaluated twice — replay must reproduce the same answer.
        val a = AffinityCell( FacetType.TOPIC, "t" ).update( Evidence( 0.5f, 1f ), 1_000 )
        val onceAtDay10 = a.decayedTo( 1_000 + 10 * DAY )
        val viaDay5 = a.decayedTo( 1_000 + 5 * DAY ).let {
            // decayedTo does not move tLast, so decaying twice from the same origin must agree
            a.decayedTo( 1_000 + 10 * DAY )
        }
        assertEquals( onceAtDay10.w, viaDay5.w, 1e-9 )
    }

    @Test
    fun `a backwards clock never amplifies old evidence`() {
        val cell = AffinityCell( FacetType.CREATOR, "qari" ).update( Evidence( 1.0f, 1.0f ), 10 * DAY )
        val earlier = cell.decayedTo( 5 * DAY )   // device clock corrected backwards
        assertTrue( "weight must not grow when the clock moves back, was ${earlier.w} vs ${cell.w}",
            earlier.w <= cell.w + 1e-9 )
    }

    // ------------------------------------------------------------------ contradiction

    @Test
    fun `loving a creator then skipping them reads as moderate and confident, not as dislike`() {
        var cell = AffinityCell( FacetType.CREATOR, "qari" )
        repeat( 12 ) { cell = cell.update( Evidence( 0.6f, 1.0f ), it.toLong() * 1000 ) }
        repeat( 3 ) { cell = cell.update( Evidence( -0.5f, 0.8f ), 13_000L + it * 1000 ) }

        assertTrue( "should still be positive, was ${cell.strength}", cell.strength > 0.2 )
        assertTrue( "should be well evidenced, was ${cell.confidence}", cell.confidence > 0.5 )
    }

    // ------------------------------------------------------------------ evidence readings

    @Test
    fun `a skip in the first seconds is the strongest ordinary rejection`() {
        val early = EvidenceExtractor.base(
            event( EventType.SKIP_NEXT, positionMs = 8_000, durationMs = 200_000 )
        )
        assertTrue( early.direction < -0.4f )
        assertTrue( early.weight > 0.7f )
    }

    @Test
    fun `a skip within two seconds is discarded as navigation`() {
        val instant = EvidenceExtractor.base(
            event( EventType.SKIP_NEXT, positionMs = 1_200, durationMs = 200_000 )
        )
        assertEquals( 0f, instant.weight, 1e-6f )
    }

    @Test
    fun `a skip near the end is mildly positive`() {
        val late = EvidenceExtractor.base(
            event( EventType.SKIP_NEXT, positionMs = 190_000, durationMs = 200_000 )
        )
        assertTrue( "they got what they came for, was ${late.direction}", late.direction > 0f )
    }

    @Test
    fun `leaving mid-item is ambiguous and is weighted as such`() {
        val middle = EvidenceExtractor.base(
            event( EventType.PLAY_END, positionMs = 90_000, durationMs = 200_000 )
        )
        assertEquals( 0f, middle.direction, 1e-6f )
        assertTrue( middle.weight <= 0.35f )
    }

    @Test
    fun `an event with no duration contributes nothing rather than a guess`() {
        val unknown = EvidenceExtractor.base(
            event( EventType.PLAY_END, positionMs = 90_000, durationMs = null )
        )
        assertEquals( 0f, unknown.weight, 1e-6f )
    }

    // ------------------------------------------------------------------ modifiers

    @Test
    fun `a positive from autoplay is trusted less than the same positive from a user choice`() {
        val e = event( EventType.PLAY_END, positionMs = 195_000, durationMs = 200_000 )
        val chosen = EvidenceExtractor.modified( e.copy( source = Provenance.SEARCH.wireName ) )
        val auto = EvidenceExtractor.modified( e.copy( source = Provenance.AUTOPLAY.wireName ) )

        assertEquals( chosen.direction, auto.direction, 1e-6f )
        assertTrue( "autoplay must be discounted: ${auto.weight} vs ${chosen.weight}",
            auto.weight < chosen.weight )
    }

    @Test
    fun `car mode discounts a positive heavily and takes a negative more seriously`() {
        val liked = event( EventType.PLAY_END, positionMs = 195_000, durationMs = 200_000 )
        val skipped = event( EventType.SKIP_NEXT, positionMs = 8_000, durationMs = 200_000 )

        val likedPhone = EvidenceExtractor.modified( liked, isCarMode = false )
        val likedCar = EvidenceExtractor.modified( liked, isCarMode = true )
        val skipPhone = EvidenceExtractor.modified( skipped, isCarMode = false )
        val skipCar = EvidenceExtractor.modified( skipped, isCarMode = true )

        assertTrue( "not skipping while driving means much less", likedCar.weight < likedPhone.weight * 0.6f )
        assertTrue( "skipping while driving is deliberate", skipCar.weight > skipPhone.weight )
    }

    @Test
    fun `a skip on recitation barely counts against it`() {
        val skip = event( EventType.SKIP_NEXT, positionMs = 8_000, durationMs = 200_000 )
        val quran = EvidenceExtractor.modified( skip, contentClass = ContentClass.QURAN )
        val other = EvidenceExtractor.modified( skip, contentClass = ContentClass.NASHEED )

        assertTrue( "recitation skips must be heavily discounted: ${quran.weight} vs ${other.weight}",
            quran.weight < other.weight * 0.5f )
    }

    @Test
    fun `recitation is protected from suppression and other classes are not`() {
        assertTrue( ContentClass.QURAN.isProtectedFromSuppression )
        assertTrue( !ContentClass.NASHEED.isProtectedFromSuppression )
        assertTrue( !ContentClass.UNKNOWN.isProtectedFromSuppression )
    }

    @Test
    fun `a skip streak is read as a mood rather than as a verdict on each item`() {
        val skip = event( EventType.SKIP_NEXT, positionMs = 8_000, durationMs = 200_000 )
        val calm = EvidenceExtractor.modified( skip, sessionSkipStreak = false )
        val streak = EvidenceExtractor.modified( skip, sessionSkipStreak = true )
        assertTrue( streak.weight < calm.weight * 0.5f )
    }

    @Test
    fun `a modifier never flips the direction of the reading`() {
        val skip = event( EventType.SKIP_NEXT, positionMs = 8_000, durationMs = 200_000 )
        val modified = EvidenceExtractor.modified(
            skip, isCarMode = true, contentClass = ContentClass.QURAN, sessionSkipStreak = true
        )
        assertTrue( "a discounted negative must stay negative", modified.direction < 0f )
    }

    // ------------------------------------------------------------------ informativeness

    @Test
    fun `the same event teaches more about the creator than about the duration band`() {
        val model = AffinityModel()
        val e = Evidence( 1.0f, 1.0f )
        model.observe( FacetType.CREATOR, "qari", e, 0 )
        model.observe( FacetType.DURATION_BAND, "20-60m", e, 0 )

        assertTrue(
            model.effect( FacetType.CREATOR, "qari", 0 ) >
            model.effect( FacetType.DURATION_BAND, "20-60m", 0 )
        )
    }

    @Test
    fun `a real change of taste is detected but a wobble is not`() {
        val model = AffinityModel()
        var recent = AffinityCell( FacetType.CREATOR, "x" )
        var trailing = AffinityCell( FacetType.CREATOR, "x" )
        repeat( 15 ) { recent = recent.update( Evidence( -0.6f, 1f ), it.toLong() ) }
        repeat( 15 ) { trailing = trailing.update( Evidence( 0.6f, 1f ), it.toLong() ) }
        assertTrue( "a sign flip with evidence behind it is a shift", model.hasShifted( recent, trailing ) )

        val thin = AffinityCell( FacetType.CREATOR, "y" ).update( Evidence( -0.6f, 1f ), 0 )
        assertTrue( "one contrary event is not a shift", !model.hasShifted( thin, trailing ) )
    }
}
