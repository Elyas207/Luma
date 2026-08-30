package app.kreate.android.service.intelligence

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The event log is the foundation everything else derives from, so these assert the properties the
 * rest of the system is allowed to assume: order is total, provenance is never lost, and a session
 * ends when it should.
 *
 * Everything here runs on a [TestClock]. None of it would be testable against the wall clock.
 */
class EventLogTest {

    private fun log( clock: LumaClock, sink: InMemoryEventSink = InMemoryEventSink() ) =
        EventLog( sink, clock, SessionKeeper( clock ) ) to sink

    @Test
    fun `provenance survives the round trip and is never inferred`() = runBlocking {
        val clock = TestClock( 1_000_000 )
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_START, Provenance.AUTOPLAY, itemId = "a" )
        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "b" )

        val stored = sink.snapshot()
        assertEquals( "autoplay", stored[0].source )
        assertEquals( "manual_browse", stored[1].source )

        // The specific corruption this guards against: an app-chosen play must never read back as
        // a user-chosen one, because nothing downstream can tell the difference afterwards.
        assertNotEquals( stored[0].source, stored[1].source )
    }

    @Test
    fun `a user-chosen play is distinguishable from one the app chose`() {
        assertTrue( Provenance.MANUAL_BROWSE.isUserChosen )
        assertTrue( Provenance.SEARCH.isUserChosen )
        assertTrue( Provenance.QUEUE.isUserChosen )
        assertTrue( !Provenance.AUTOPLAY.isUserChosen )
        assertTrue( !Provenance.RECOMMENDATION.isUserChosen )
        assertTrue( !Provenance.RESUME.isUserChosen )
    }

    @Test
    fun `an unknown provenance on the wire degrades to external rather than to a user choice`() {
        // An old build reading a newer log must never upgrade an unknown source into evidence that
        // the user chose something.
        assertEquals( Provenance.EXTERNAL, Provenance.fromWire( "teleport" ) )
        assertEquals( Provenance.EXTERNAL, Provenance.fromWire( null ) )
        assertTrue( !Provenance.fromWire( "teleport" ).isUserChosen )
    }

    @Test
    fun `events in the same millisecond keep the order they were recorded in`() = runBlocking {
        val clock = TestClock( 5_000_000 )
        val ( events, sink ) = log( clock )

        // A skip emits an end and a start together; they must not reorder.
        repeat( 20 ) { events.record( EventType.PLAY_START, Provenance.QUEUE, itemId = "i$it" ) }

        val ids = sink.snapshot().map { it.id }
        assertEquals( "ids must be unique", ids.size, ids.toSet().size )
        assertEquals( "sorting by id must reproduce insertion order", ids, ids.sorted() )
    }

    @Test
    fun `a backwards clock jump does not reorder the log`() = runBlocking {
        val clock = TestClock( 9_000_000 )
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_START, Provenance.SEARCH, itemId = "first" )
        // NTP correction, a manual clock change, or the end of daylight saving.
        clock.setTo( 9_000_000 - 60_000 )
        events.record( EventType.PLAY_START, Provenance.SEARCH, itemId = "second" )

        val ids = sink.snapshot().map { it.id }
        assertEquals( "the second event must still sort after the first", ids, ids.sorted() )
    }

    @Test
    fun `activity within thirty minutes stays one session`() = runBlocking {
        val clock = TestClock( 0 )
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "a" )
        clock.advanceBy( 29 * 60 * 1000L )
        events.record( EventType.PLAY_START, Provenance.AUTOPLAY, itemId = "b" )

        val sessions = sink.snapshot().map { it.sessionId }.toSet()
        assertEquals( 1, sessions.size )
    }

    @Test
    fun `a gap over thirty minutes starts a new session`() = runBlocking {
        val clock = TestClock( 0 )
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "a" )
        clock.advanceBy( 31 * 60 * 1000L )
        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "b" )

        val sessions = sink.snapshot().map { it.sessionId }
        assertNotEquals( sessions[0], sessions[1] )
    }

    @Test
    fun `a large backwards clock jump also starts a new session`() = runBlocking {
        // We cannot tell how much real time passed, so treating it as continuous would silently
        // merge two unrelated listening sessions into one.
        val clock = TestClock( 100_000_000 )
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "a" )
        clock.setTo( 100_000_000 - 2 * 60 * 60 * 1000L )
        events.record( EventType.PLAY_START, Provenance.MANUAL_BROWSE, itemId = "b" )

        val sessions = sink.snapshot().map { it.sessionId }
        assertNotEquals( sessions[0], sessions[1] )
    }

    @Test
    fun `the timezone offset is recorded per event, not derived later`() = runBlocking {
        val clock = TestClock( 1_700_000_000_000, offsetMinutes = 600 )   // UTC+10
        val ( events, sink ) = log( clock )

        events.record( EventType.PLAY_END, Provenance.AUTOPLAY, itemId = "a" )
        clock.setTimezoneOffsetMinutes( 0 )                              // flew to UTC
        events.record( EventType.PLAY_END, Provenance.AUTOPLAY, itemId = "b" )

        val stored = sink.snapshot()
        assertEquals( 600, stored[0].tzOffsetMinutes )
        assertEquals( 0, stored[1].tzOffsetMinutes )
    }

    @Test
    fun `duration is captured at event time`() = runBlocking {
        val clock = TestClock( 42 )
        val ( events, sink ) = log( clock )

        events.record(
            EventType.PLAY_END,
            Provenance.PLAYLIST,
            itemId = "a",
            positionMs = 90_000,
            durationMs = 100_000
        )

        val e = sink.snapshot().single()
        assertEquals( 90_000L, e.positionMs )
        assertEquals( 100_000L, e.durationMs )
        assertEquals( 42L, e.ts )
    }
}
