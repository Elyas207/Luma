package app.kreate.android.service.handoff

/**
 * Moving what you're listening to from one device to another.
 *
 * **The QR code *is* the payload.** There is no pairing handshake, no server, no account and no
 * network call — everything needed to resume is encoded in the image on screen. That decision
 * drives everything else about this feature:
 *
 * - It works with no internet, which is exactly the situation in a car park or an underground
 *   garage where you'd actually want to move playback to the car.
 * - There is nothing to fail: no session to expire, no port to be blocked, no device to be "not
 *   found". If the camera can read the square, the transfer works.
 * - Nothing leaves the two devices, so there is no privacy question to answer.
 *
 * The cost is capacity: a QR code at the error correction this uses holds around 1,273 bytes, so a
 * queue is capped at [MAX_ITEMS]. Truncation is from the *current position onward*, because what is
 * behind you does not need to travel.
 */
object Handoff {

    /** Bumped if the wire format ever changes, so an old device fails clearly instead of oddly. */
    const val VERSION = 1

    /** Identifies our codes so a scanner can reject unrelated QR codes with a useful message. */
    const val SCHEME = "kreate"

    /**
     * Cap on carried tracks.
     *
     * Derived, not guessed. A QR code at error-correction level H tops out near 1,273 bytes; the
     * fixed prefix costs ~18 and each id costs 12 (11 characters plus a separator), which leaves
     * room for about 104. This sits below that with headroom for longer ids, and is verified by
     * `HandoffQrTest` — an over-large payload throws at encode time, so getting this wrong would
     * break handoff only for people with long queues.
     *
     * Level H is kept rather than traded for capacity because this code gets read at an angle,
     * from a phone held over a dashboard, sometimes in sunlight. 90 tracks is far more than anyone
     * needs to continue a session.
     */
    const val MAX_ITEMS = 90

    /**
     * Encode a queue as a transfer payload.
     *
     * Format: `kreate:1:<startIndex>:<positionMs>:<id>,<id>,...`
     *
     * Deliberately plain text rather than JSON or protobuf — the field names alone would cost a
     * third of the capacity, and there is no schema here worth carrying.
     *
     * Note the payload stays in QR *byte* mode. Alphanumeric mode is denser, but it has no
     * lowercase, and YouTube ids are case-sensitive — normalising case to gain capacity would
     * silently corrupt every id.
     *
     * @param items every song id in the queue, in order
     * @param currentIndex which one is playing
     * @param positionMs how far into it
     */
    fun encode(
        items: List<String>,
        currentIndex: Int,
        positionMs: Long
    ): String {
        if ( items.isEmpty() ) return ""

        val safeIndex = currentIndex.coerceIn( 0, items.lastIndex )

        // Carry the current song and what follows. History is not worth the capacity.
        val carried = items.drop( safeIndex ).take( MAX_ITEMS )

        return buildString {
            append( SCHEME ).append( ':' )
            append( VERSION ).append( ':' )
            // The current track always becomes index 0 of the carried list
            append( 0 ).append( ':' )
            append( positionMs.coerceAtLeast( 0 ) ).append( ':' )
            append( carried.joinToString( "," ) )
        }
    }

    /**
     * Parse a scanned payload, or `null` if this is not one of ours.
     *
     * Returning null rather than throwing because the overwhelmingly common failure is scanning
     * some *other* QR code — a wifi login, a menu, a parking sign. That is a normal thing for a
     * person to do and deserves "that isn't one of ours", not a crash.
     */
    fun decode( raw: String ): HandoffPayload? {
        val trimmed = raw.trim()
        val parts = trimmed.split( ':', limit = 5 )

        if ( parts.size != 5 ) return null
        if ( !parts[0].equals( SCHEME, ignoreCase = true ) ) return null

        val version = parts[1].toIntOrNull() ?: return null
        // A newer device may send a format this build cannot read. Say so rather than guess.
        if ( version > VERSION ) return null

        val index = parts[2].toIntOrNull() ?: return null
        val position = parts[3].toLongOrNull() ?: return null

        val ids = parts[4].split( ',' )
                          .map( String::trim )
                          .filter { it.isNotEmpty() }

        if ( ids.isEmpty() ) return null

        return HandoffPayload(
            songIds = ids,
            startIndex = index.coerceIn( 0, ids.lastIndex ),
            positionMs = position.coerceAtLeast( 0 )
        )
    }
}

data class HandoffPayload(
    val songIds: List<String>,
    val startIndex: Int,
    val positionMs: Long
)
