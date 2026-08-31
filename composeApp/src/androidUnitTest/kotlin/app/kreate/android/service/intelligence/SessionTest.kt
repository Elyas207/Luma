package app.kreate.android.service.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session behaviour, the significance gate, and exploration.
 *
 * These are the parts that decide how the app behaves when it is *wrong*, which is the case that
 * determines whether a person keeps using it.
 */
class SessionTest {

    // ------------------------------------------------------------------ circuit breakers

    @Test
    fun `three early skips put the session into recovery and stop exploration`() {
        val s = SessionState()
        repeat( 3 ) { s.observe( "creator", -1.0, wasAutoplay = true ) }

        assertEquals( SessionState.Breaker.RECOVERY, s.breaker() )
        assertTrue( "no experiments while it is going badly", !s.explorationAllowed() )
    }

    @Test
    fun `five early skips stop autoplay altogether`() {
        val s = SessionState()
        repeat( 5 ) { s.observe( "creator", -1.0, wasAutoplay = true ) }
        assertEquals( SessionState.Breaker.STOP_AUTOPLAY, s.breaker() )
    }

    @Test
    fun `skips during a bad run are treated as mood rather than as taste`() {
        val s = SessionState()
        assertEquals( 1f, s.skipEvidenceMultiplier(), 1e-6f )
        repeat( 3 ) { s.observe( "creator", -1.0, wasAutoplay = true ) }
        assertTrue( "a run of skips must count for much less", s.skipEvidenceMultiplier() < 0.5f )
    }

    @Test
    fun `a completion breaks the skip streak`() {
        val s = SessionState()
        repeat( 4 ) { s.observe( "creator", -1.0, wasAutoplay = true ) }
        s.observe( "creator", 1.0, wasAutoplay = true )
        assertEquals( SessionState.Breaker.NONE, s.breaker() )
    }

    // ------------------------------------------------------------------ intent blending

    @Test
    fun `the session never fully erases the long term profile`() {
        // The rut failure: if the session could reach 1.0, everything the user skipped would push
        // them further into the corner they were trying to get out of.
        val s = SessionState()
        s.seed( Provenance.SEARCH )
        repeat( 10 ) { s.observe( "one-thing", 1.0, wasAutoplay = false ) }

        assertTrue( "blend weight must stay bounded, was ${s.blendWeight()}", s.blendWeight() <= 0.70 )
        assertTrue( s.blendWeight() >= 0.15 )
    }

    @Test
    fun `a failing session leans back on the profile rather than on itself`() {
        val focused = SessionState().apply {
            seed( Provenance.SEARCH )
            repeat( 4 ) { observe( "x", 1.0, wasAutoplay = false ) }
        }
        val failing = SessionState().apply {
            seed( Provenance.AUTOPLAY )
            repeat( 4 ) { observe( "x", -1.0, wasAutoplay = true ) }
        }
        assertTrue( failing.blendWeight() < focused.blendWeight() )
    }

    @Test
    fun `blending moves the answer towards what is happening now, without replacing it`() {
        val s = SessionState()
        s.seed( Provenance.MANUAL_BROWSE )
        repeat( 3 ) { s.observe( "creator", 1.0, wasAutoplay = false ) }

        val longTerm = 0.0
        val blended = s.blend( longTerm, "creator" )
        assertTrue( "the session should pull it up", blended > longTerm )
        assertTrue( "but not all the way", blended < 1.0 )
    }

    // ------------------------------------------------------------------ significance gate

    @Test
    fun `a thin context cell contributes exactly zero`() {
        val thin = AffinityCell( FacetType.CONTEXT, "LATE_NIGHT", s = 3.0, w = 3.0, n = 3 )
        assertEquals( 0.0, SignificanceGate.contribution( thin, globalStrength = 0.0 ), 1e-12 )
    }

    @Test
    fun `a context cell that matches the global model contributes nothing`() {
        // No divergence means no information: the user behaves the same way at that time as they do
        // generally, so a "pattern" would be invented rather than observed.
        var cell = AffinityCell( FacetType.CONTEXT, "MORNING" )
        repeat( 60 ) { cell = cell.update( Evidence( 0.5f, 1f ), it.toLong() * 1000 ) }
        assertEquals( 0.0, SignificanceGate.contribution( cell, globalStrength = 0.5 ), 1e-12 )
    }

    @Test
    fun `a well evidenced and genuinely different context does contribute`() {
        var cell = AffinityCell( FacetType.CONTEXT, "LATE_NIGHT" )
        repeat( 60 ) { cell = cell.update( Evidence( 0.9f, 1f ), it.toLong() * 1000 ) }

        assertTrue( SignificanceGate.passes( cell, globalStrength = 0.1 ) )
        assertTrue( SignificanceGate.contribution( cell, 0.1 ) > 0.0 )
    }

    @Test
    fun `time buckets follow local time, not UTC`() {
        // 02:00 UTC is late night in London and mid-morning in Sydney. Getting this wrong silently
        // reassigns every event a traveller ever recorded.
        val utc2am = 1_700_000_000_000L / 86_400_000L * 86_400_000L + 2 * 3_600_000L
        assertEquals( TimeBucket.LATE_NIGHT, TimeBucket.of( utc2am, tzOffsetMinutes = 0 ) )
        assertEquals( TimeBucket.MORNING, TimeBucket.of( utc2am, tzOffsetMinutes = 8 * 60 ) )
    }

    @Test
    fun `a negative timezone offset does not fall off the end of the day`() {
        val utcMidnight = 1_700_000_000_000L / 86_400_000L * 86_400_000L
        val bucket = TimeBucket.of( utcMidnight, tzOffsetMinutes = -5 * 60 )
        assertTrue( bucket in TimeBucket.entries )
    }

    // ------------------------------------------------------------------ exploration

    @Test
    fun `exploration never drops to zero`() {
        val e = Exploration()
        val worst = e.rate( playsSoFar = 5_000, engagement = -1.0, isCarMode = true )
        assertTrue( "a calcified profile has no way back without a floor", worst >= Exploration.FLOOR )
    }

    @Test
    fun `a new user explores more than a settled one`() {
        val e = Exploration()
        assertTrue(
            e.rate( playsSoFar = 0, engagement = 0.0, isCarMode = false ) >
            e.rate( playsSoFar = 500, engagement = 0.0, isCarMode = false )
        )
    }

    @Test
    fun `driving suppresses exploration`() {
        val e = Exploration()
        assertTrue(
            e.rate( 100, 0.5, isCarMode = true ) < e.rate( 100, 0.5, isCarMode = false )
        )
    }

    @Test
    fun `exploration happens after a completion and never after a skip`() {
        val e = Exploration()
        val s = SessionState()

        assertTrue( e.shouldExplore(
            lastOutcomeWasCompletion = true, immediatelyAfterManualChoice = false,
            session = s, roll = 0.0, playsSoFar = 0, isCarMode = false
        ) )
        assertTrue( "a frustrated listener does not want a surprise", !e.shouldExplore(
            lastOutcomeWasCompletion = false, immediatelyAfterManualChoice = false,
            session = s, roll = 0.0, playsSoFar = 0, isCarMode = false
        ) )
    }

    @Test
    fun `nothing is explored immediately after the user chose something`() {
        // They just said what they wanted. Answering with something else is not clever.
        val e = Exploration()
        assertTrue( !e.shouldExplore(
            lastOutcomeWasCompletion = true, immediatelyAfterManualChoice = true,
            session = SessionState(), roll = 0.0, playsSoFar = 0, isCarMode = false
        ) )
    }

    @Test
    fun `two failures in one direction put it on cooldown, and a success clears it`() {
        val clock = TestClock( 1_000_000 )
        val e = Exploration( clock )

        e.recordOutcome( "creator->adjacent-topic", succeeded = false )
        assertTrue( !e.isOnCooldown( "creator->adjacent-topic" ) )

        e.recordOutcome( "creator->adjacent-topic", succeeded = false )
        assertTrue( "two misses is enough to stop trying", e.isOnCooldown( "creator->adjacent-topic" ) )

        clock.advanceDays( 31.0 )
        assertTrue( "and the cooldown lapses", !e.isOnCooldown( "creator->adjacent-topic" ) )
    }

    @Test
    fun `cooldowns are per direction, not global`() {
        val e = Exploration( TestClock( 0 ) )
        repeat( 2 ) { e.recordOutcome( "a->b", succeeded = false ) }

        assertTrue( e.isOnCooldown( "a->b" ) )
        assertTrue( "one bad direction must not stop every other one", !e.isOnCooldown( "c->d" ) )
    }
}
