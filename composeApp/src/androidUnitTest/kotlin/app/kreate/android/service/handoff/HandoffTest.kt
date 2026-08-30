package app.kreate.android.service.handoff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The handoff payload is the entire transfer protocol, so it carries all the risk.
 *
 * A malformed or misparsed payload means someone's queue silently arrives wrong on the other
 * device, which is worse than a visible failure — hence the emphasis here on rejecting anything
 * unexpected rather than salvaging it.
 */
class HandoffTest {

    private val ids = listOf( "aaaaaaaaaaa", "bbbbbbbbbbb", "ccccccccccc", "ddddddddddd" )

    @Test
    fun `round trips a queue`() {
        val payload = Handoff.decode( Handoff.encode( ids, currentIndex = 0, positionMs = 4_200 ) )

        assertEquals( ids, payload?.songIds )
        assertEquals( 0, payload?.startIndex )
        assertEquals( 4_200L, payload?.positionMs )
    }

    @Test
    fun `carries only the current track onward`() {
        // What is behind you does not need to travel; capacity is better spent on what's next.
        val payload = Handoff.decode( Handoff.encode( ids, currentIndex = 2, positionMs = 0 ) )

        assertEquals( listOf( "ccccccccccc", "ddddddddddd" ), payload?.songIds )
        assertEquals( 0, payload?.startIndex )
    }

    @Test
    fun `preserves playback position so the track resumes where it was left`() {
        val payload = Handoff.decode( Handoff.encode( ids, currentIndex = 1, positionMs = 93_500 ) )

        assertEquals( 93_500L, payload?.positionMs )
    }

    @Test
    fun `caps the queue at the QR capacity limit`() {
        val huge = List( 500 ) { "id%08d".format( it ) }

        val payload = Handoff.decode( Handoff.encode( huge, currentIndex = 0, positionMs = 0 ) )

        assertEquals( Handoff.MAX_ITEMS, payload?.songIds?.size )
    }

    @Test
    fun `an out of range index cannot produce a broken payload`() {
        val payload = Handoff.decode( Handoff.encode( ids, currentIndex = 99, positionMs = 0 ) )

        // Clamped to the last track rather than yielding an empty or corrupt queue
        assertEquals( listOf( "ddddddddddd" ), payload?.songIds )
    }

    @Test
    fun `rejects a QR code that belongs to something else`() {
        // The common real-world case: someone scans a wifi login or a restaurant menu.
        assertNull( Handoff.decode( "https://example.com" ) )
        assertNull( Handoff.decode( "WIFI:S:MyNetwork;T:WPA;P:hunter2;;" ) )
        assertNull( Handoff.decode( "" ) )
        assertNull( Handoff.decode( "kreate" ) )
    }

    @Test
    fun `rejects a payload from a newer app version`() {
        // Better to say "update the app" than to misread a format that changed.
        assertNull( Handoff.decode( "kreate:99:0:0:aaaaaaaaaaa" ) )
    }

    @Test
    fun `rejects structurally valid but empty queues`() {
        assertNull( Handoff.decode( "kreate:1:0:0:" ) )
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        val encoded = "  " + Handoff.encode( ids, 0, 0 ) + "\n"

        assertEquals( ids, Handoff.decode( encoded )?.songIds )
    }

    @Test
    fun `an empty queue encodes to nothing rather than a misleading payload`() {
        assertTrue( Handoff.encode( emptyList(), 0, 0 ).isEmpty() )
    }

    @Test
    fun `a full payload stays within QR alphanumeric capacity`() {
        // ~2900 chars is the practical ceiling for a QR code at a readable density.
        val huge = List( 500 ) { "abcdefghijk" }

        assertTrue(
            "payload must fit in a scannable QR code",
            Handoff.encode( huge, 0, 999_999 ).length < 2_900
        )
    }
}
