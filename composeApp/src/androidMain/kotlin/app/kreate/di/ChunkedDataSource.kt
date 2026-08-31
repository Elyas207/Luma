@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import android.net.Uri
import co.touchlab.kermit.Logger

/**
 * Fetches a stream in fixed-size ranges while presenting it upstream as one continuous read.
 *
 * ## Why this exists
 *
 * googlevideo refuses a request for a whole file and serves the same bytes happily in pieces.
 * Measured against a live url: `bytes=0-1` → 206, `bytes=0-450000` → 206, `bytes=0-460000` → 403,
 * whole-file → 403. ExoPlayer, given a content length, asks for `bytes=0-<clen-1>` in a single
 * request — for a long recitation that is a ~1.5 GB range — and is refused before a byte of audio
 * is decoded. Every layer above looked healthy, which is what made it hard to see: the client sweep
 * succeeded, the signature deciphered, the url validated, and playback still died instantly.
 *
 * ## Why not just shorten the DataSpec
 *
 * That was the first attempt and it is worth recording, because it *looks* like it works. Bounding
 * the resolved `DataSpec` to 256 KB does produce a successful ranged request — the first chunk
 * played about seven seconds of audio. But `ProgressiveMediaPeriod` then believes the file *is*
 * 256 KB, reads to the end of it and raises `EOFException`. The player has to be told the real
 * length while the *transport* is what gets split up, which is precisely this class.
 *
 * ## Behaviour
 *
 * `open()` reports the full remaining length, so the player's duration, seek bar and buffering
 * logic are unaffected. Internally the first range is opened immediately; each time a range is
 * exhausted the next is opened transparently inside `read()`. A seek arrives as a fresh `open()`
 * at a new position and simply starts a new range there.
 */
class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long,
    /**
     * Told the cache key of a url the CDN has stopped serving, so it can be re-minted.
     *
     * A googlevideo url survives only a handful of range requests, well inside its advertised
     * lifetime, so a mid-track 403 means "this url is used up" rather than "this track is gone".
     * Without this the resolver keeps handing the same dead url to every retry.
     */
    private val onUrlSpent: ( String ) -> Unit = {}
) : DataSource {

    private val log = Logger.withTag( "chunked" )

    private var spec: DataSpec? = null
    private var position: Long = 0
    /** Bytes still owed to the caller across all remaining ranges, or [C.LENGTH_UNSET]. */
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    /** Bytes still readable from the range currently open. */
    private var chunkRemaining: Long = 0
    private var chunkOpen = false

    /**
     * Bytes actually delivered by the range currently open.
     *
     * Needed because the total length is not always known here: `CacheDataSource` asks for
     * "everything from this offset" with [C.LENGTH_UNSET]. Without this counter the first range
     * ending looks identical to the track ending, and playback stops a few seconds in with
     * `EOFException` — which is exactly the bug the first version of this class shipped with. A
     * range that delivered a *full* chunk is presumed to have been cut short by the chunk size; one
     * that delivered less has genuinely reached the end of the file.
     */
    private var chunkDelivered: Long = 0
    private var requestedChunk: Long = 0

    override fun addTransferListener( transferListener: TransferListener ) =
        upstream.addTransferListener( transferListener )

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun open( dataSpec: DataSpec ): Long {
        spec = dataSpec
        position = dataSpec.position
        bytesRemaining = dataSpec.length

        openNextChunk()

        // The *real* remaining length, not the chunk's. This is what stops the player treating the
        // end of a chunk as the end of the track.
        return bytesRemaining
    }

    private fun openNextChunk() {
        val current = spec ?: return

        val length = if ( bytesRemaining == C.LENGTH_UNSET.toLong() )
            chunkSize
        else
            minOf( chunkSize, bytesRemaining )

        val chunkSpec = current.buildUpon()
                               .setPosition( position )
                               .setLength( length )
                               .build()

        chunkRemaining = try {
            upstream.open( chunkSpec )
        } catch ( e: InvalidResponseCodeException ) {
            // 403 is the CDN retiring the url, not a verdict on the track. Retiring it here means
            // ExoPlayer's own retry re-enters the resolver and continues from this same position
            // on a freshly minted url, instead of replaying the rejection until it gives up.
            if ( e.responseCode == HTTP_FORBIDDEN )
                current.key?.let {
                    log.d { "chunk at pos=$position refused (403); asking for a fresh url" }
                    onUrlSpent( it )
                }
            throw e
        }
        log.d { "open pos=$position len=$length -> $chunkRemaining (total remaining=$bytesRemaining)" }
        chunkOpen = true
        chunkDelivered = 0
        requestedChunk = length
    }

    /** Whether the track can still have bytes after the range that just ended. */
    private fun moreLikelyRemains(): Boolean = when {
        bytesRemaining == 0L -> false
        bytesRemaining != C.LENGTH_UNSET.toLong() -> true
        // Length unknown: a range that delivered everything it was asked for was almost certainly
        // truncated by the chunk size rather than by the end of the file.
        else -> chunkDelivered >= requestedChunk && requestedChunk > 0
    }

    override fun read( buffer: ByteArray, offset: Int, length: Int ): Int {
        if ( bytesRemaining == 0L ) return C.RESULT_END_OF_INPUT
        if ( length == 0 ) return 0

        if ( chunkOpen && chunkRemaining == 0L ) {
            if ( !moreLikelyRemains() ) { log.d { "EOF: chunk spent, nothing more (rem=$bytesRemaining delivered=$chunkDelivered/$requestedChunk)" }; return C.RESULT_END_OF_INPUT }
            // This range is spent but the track is not: roll on to the next one. The caller never
            // learns that the transport restarted.
            upstream.close()
            chunkOpen = false
            openNextChunk()
        }

        val allowed = if ( chunkRemaining == C.LENGTH_UNSET.toLong() )
            length
        else
            minOf( length.toLong(), chunkRemaining ).toInt()

        val read = upstream.read( buffer, offset, allowed )

        if ( read == C.RESULT_END_OF_INPUT ) {
            // A range can end before the byte count suggested, so treat it the same way: if the
            // track still owes us bytes, open the next range rather than reporting EOF upward.
            if ( moreLikelyRemains() ) {
                log.d { "chunk EOF at pos=$position, opening next" }
                upstream.close()
                chunkOpen = false
                openNextChunk()
                return read( buffer, offset, length )
            }
            log.d { "EOF upstream: rem=$bytesRemaining delivered=$chunkDelivered/$requestedChunk" }
            return C.RESULT_END_OF_INPUT
        }

        position += read
        chunkDelivered += read
        if ( chunkRemaining != C.LENGTH_UNSET.toLong() ) chunkRemaining -= read
        if ( bytesRemaining != C.LENGTH_UNSET.toLong() ) bytesRemaining -= read

        return read
    }

    override fun close() {
        if ( chunkOpen ) {
            chunkOpen = false
            upstream.close()
        }
        spec = null
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
    }

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        /**
         * Comfortably inside the ~450 KB ceiling measured on live urls, while keeping a
         * three-minute track to roughly a dozen requests — the forward buffer issues these well
         * ahead of the playhead, so the extra round trips are never heard.
         */
        private val chunkSize: Long = 256L * 1024,
        private val onUrlSpent: ( String ) -> Unit = {}
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            ChunkedDataSource( upstreamFactory.createDataSource(), chunkSize, onUrlSpent )
    }
}
