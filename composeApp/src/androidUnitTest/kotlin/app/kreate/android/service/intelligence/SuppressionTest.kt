package app.kreate.android.service.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 86_400_000L

/**
 * Suppression is the part of the system most able to make a user feel the app is broken, so these
 * assert the guarantees rather than the mechanics: recitation is never suppressed, three strikes
 * have to fall on different days, and nothing automatic lasts forever.
 */
class SuppressionTest {

    private fun register( start: Long = 1_000 * DAY ) =
        TestClock( start ).let { it to SuppressionRegister( it ) }

    @Test
    fun `three early skips on distinct days suppress an item`() {
        val ( clock, reg ) = register()
        repeat( 3 ) {
            reg.recordEarlySkip( "n1", ContentClass.NASHEED )
            clock.advanceDays( 1.0 )
        }
        assertTrue( reg.isSuppressed( "n1" ) )
    }

    @Test
    fun `three skips in one evening are one strike, not three`() {
        // Frustration is not preference. Skipping the same track repeatedly in a single sitting
        // says "not right now", and treating it as a verdict is how an app loses a track someone
        // actually likes.
        val ( clock, reg ) = register()
        repeat( 3 ) {
            reg.recordEarlySkip( "n1", ContentClass.NASHEED )
            clock.advanceBy( 5 * 60 * 1000 )
        }
        assertTrue( "one day of skips must not suppress", !reg.isSuppressed( "n1" ) )
        assertEquals( 1, reg.entry( "n1" )!!.strikes )
    }

    @Test
    fun `recitation is never suppressed, however often it is skipped`() {
        val ( clock, reg ) = register()
        repeat( 20 ) {
            reg.recordEarlySkip( "q1", ContentClass.QURAN )
            clock.advanceDays( 1.0 )
        }
        assertTrue( "recitation must never be suppressed by inference", !reg.isSuppressed( "q1" ) )
        assertEquals( "no strike should even have been recorded", null, reg.entry( "q1" ) )
    }

    @Test
    fun `suppression expires by itself after ninety days`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "n1", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        assertTrue( reg.isSuppressed( "n1" ) )

        // Measured from the last strike, not from the start of the run — the three strike days
        // shift the baseline, and an earlier version of this test forgot that and blamed the code.
        val lastStrike = reg.entry( "n1" )!!.lastStrikeMillis

        clock.setTo( lastStrike + SuppressionRegister.EXPIRY_MS - DAY )
        assertTrue( "still suppressed just before the expiry", reg.isSuppressed( "n1" ) )

        clock.setTo( lastStrike + SuppressionRegister.EXPIRY_MS + DAY )
        assertTrue( "a wrong automatic decision must undo itself", !reg.isSuppressed( "n1" ) )
    }

    @Test
    fun `any positive clears the strikes and amplifies the next good signal`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "n1", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        assertTrue( reg.isSuppressed( "n1" ) )

        val multiplier = reg.recordPositive( "n1" )

        assertTrue( "one positive is enough to restore it", !reg.isSuppressed( "n1" ) )
        assertTrue( "recovery should be faster than the mistake was", multiplier > 1f )
    }

    @Test
    fun `a positive on something never suppressed does not get amplified`() {
        val ( _, reg ) = register()
        assertEquals( 1f, reg.recordPositive( "never-seen" ), 1e-6f )
    }

    @Test
    fun `a user override stops suppression and survives further skips`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "n1", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        reg.override( "n1" )
        assertTrue( !reg.isSuppressed( "n1" ) )

        repeat( 5 ) { reg.recordEarlySkip( "n1", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        assertTrue( "the user's word beats further inference", !reg.isSuppressed( "n1" ) )
    }

    @Test
    fun `the suppressed list is exactly what the user would be shown`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "a", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        reg.recordEarlySkip( "b", ContentClass.NASHEED )     // only one strike

        assertEquals( listOf( "a" ), reg.suppressedIds() )
    }

    @Test
    fun `pruning removes lapsed entries and keeps overrides`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "a", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        repeat( 3 ) { reg.recordEarlySkip( "b", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        reg.override( "b" )

        clock.advanceDays( 120.0 )
        val pruned = reg.pruneExpired()

        assertEquals( 1, pruned )
        assertEquals( null, reg.entry( "a" ) )
        assertTrue( "an override is the user's, not ours to prune", reg.entry( "b" ) != null )
    }

    @Test
    fun `pruning is housekeeping and never changes the answer`() {
        val ( clock, reg ) = register()
        repeat( 3 ) { reg.recordEarlySkip( "a", ContentClass.NASHEED ); clock.advanceDays( 1.0 ) }
        clock.advanceDays( 120.0 )

        val beforePrune = reg.isSuppressed( "a" )
        reg.pruneExpired()
        assertEquals( beforePrune, reg.isSuppressed( "a" ) )
    }
}

/**
 * The recording gate. Trust in this feature rests on "off" meaning nothing is written, so it is
 * asserted directly rather than inferred from the absence of rows in a database somewhere.
 */
class RecordingGateTest {

    @Test
    fun `a private session writes nothing, even with learning enabled`() {
        assertTrue( !Intelligence.shouldRecord( learningEnabled = true, privateSession = true, itemId = "a" ) )
    }

    @Test
    fun `learning switched off writes nothing, even outside a private session`() {
        assertTrue( !Intelligence.shouldRecord( learningEnabled = false, privateSession = false, itemId = "a" ) )
    }

    @Test
    fun `an event with no item is never written`() {
        assertTrue( !Intelligence.shouldRecord( true, false, null ) )
        assertTrue( !Intelligence.shouldRecord( true, false, "" ) )
        assertTrue( !Intelligence.shouldRecord( true, false, "   " ) )
    }

    @Test
    fun `the ordinary case records`() {
        assertTrue( Intelligence.shouldRecord( true, false, "a" ) )
    }
}
