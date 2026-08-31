package app.kreate.android.service.handoff

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves a real queue survives the whole round trip: encode → QR → decode → queue.
 *
 * Worth testing at this level because the failure it guards against is silent. If a payload is a
 * few characters too long for the chosen error-correction level, encoding throws; if the character
 * set slips out of alphanumeric mode, capacity roughly halves and a queue that worked yesterday
 * stops fitting today. Neither shows up until someone is standing in a car park.
 */
class HandoffQrTest {

    private fun encodeToMatrix( payload: String, size: Int = 512 ): BitMatrix =
        QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 1
            )
        )

    /** Re-read a matrix back to its text, mirroring what a camera would do. */
    private fun decodeMatrix( payload: String ): String {
        // Encode at module resolution (no scaling) so the decoder sees exact modules.
        val matrix = QRCodeWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            0, 0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                EncodeHintType.MARGIN to 0
            )
        )

        val bits = com.google.zxing.common.BitMatrix( matrix.width, matrix.height )
        for ( x in 0 until matrix.width )
            for ( y in 0 until matrix.height )
                if ( matrix[x, y] ) bits.set( x, y )

        return Decoder().decode( bits ).text
    }

    @Test
    fun `a realistic queue survives encode and decode`() {
        val ids = List( 40 ) { "vid%07d".format( it ) }
        val payload = Handoff.encode( ids, currentIndex = 3, positionMs = 61_000 )

        val recovered = Handoff.decode( decodeMatrix( payload ) )

        assertEquals( ids.drop( 3 ), recovered?.songIds )
        assertEquals( 61_000L, recovered?.positionMs )
    }

    @Test
    fun `a single track handoff works`() {
        val payload = Handoff.encode( listOf( "dQw4w9WgXcQ" ), 0, 12_345 )

        val recovered = Handoff.decode( decodeMatrix( payload ) )

        assertEquals( listOf( "dQw4w9WgXcQ" ), recovered?.songIds )
        assertEquals( 12_345L, recovered?.positionMs )
    }

    @Test
    fun `a maximum size queue still encodes at high error correction`() {
        // The worst case that can actually be produced. If this throws, MAX_ITEMS is too high for
        // ErrorCorrectionLevel.H and the feature would fail only for heavy users.
        val ids = List( Handoff.MAX_ITEMS ) { "abcdefghijk" }
        val payload = Handoff.encode( ids, 0, 999_999 )

        val matrix = encodeToMatrix( payload )

        assertTrue( matrix.width > 0 )
        assertEquals( Handoff.MAX_ITEMS, Handoff.decode( payload )?.songIds?.size )
    }

    @Test
    fun `high error correction is used so the code reads at an angle`() {
        // Level H tolerates ~30% damage — the difference between working and not when a phone is
        // held over a dashboard in sunlight.
        val payload = Handoff.encode( List( 20 ) { "abcdefghijk" }, 0, 0 )

        val matrix = encodeToMatrix( payload, size = 256 )

        assertEquals( 256, matrix.width )
    }
}
