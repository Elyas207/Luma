package app.kreate.di

import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.YTPlayerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Guards the stream-url cache deadline.
 *
 * The regression being locked out: expiry used to be checked by comparing YouTube's *duration*
 * (`expiresInSeconds`, ~6 h) against `System.currentTimeMillis()` (~1.7e12). The duration is always
 * the smaller number, so the check read "expired" every single time and the cache never served a
 * hit — every playback re-ran a full multi-client resolve.
 */
class StreamExpiryTest {

    private val now = 1_700_000_000_000L      // fixed instant; nothing here reads the wall clock

    @Test
    fun `deadline is stamped forward from now, minus the safety margin`() {
        val sixHours = 6.hours.inWholeSeconds.toInt()

        val deadline = streamDeadlineMillis( now, sixHours )

        assertEquals( now + 6.hours.inWholeMilliseconds - 30.seconds.inWholeMilliseconds, deadline )
    }

    @Test
    fun `deadline lands in the future for a realistic expiry`() {
        // The actual regression: this must be greater than `now`, which the old duration-vs-epoch
        // comparison could never satisfy.
        assertTrue( streamDeadlineMillis( now, 21_540 ) > now )
    }

    @Test
    fun `a freshly stamped url is not expired and a lapsed one is`() {
        val fresh = CachedStream( DATA_UNUSED, System.currentTimeMillis() + 60_000 )
        val lapsed = CachedStream( DATA_UNUSED, System.currentTimeMillis() - 1 )

        assertFalse( fresh.isExpired )
        assertTrue( lapsed.isExpired )
    }

    @Test
    fun `an expiry shorter than the safety margin is already past`() {
        // A url with 10s left must not be handed out — the margin has to push it behind `now`.
        assertTrue( streamDeadlineMillis( now, 10 ) < now )
    }

    private companion object {

        /** [CachedStream.isExpired] never reads the payload; this exists only to satisfy the type. */
        val DATA_UNUSED = YTPlayerUtils.PlaybackData(
            audioConfig = null,
            videoDetails = null,
            playbackTracking = null,
            format = PlayerResponse.StreamingData.Format(
                itag = 251,
                url = "https://example.invalid/stream",
                mimeType = "audio/webm; codecs=\"opus\"",
                bitrate = 128_000,
                width = null,
                height = null,
                contentLength = null,
                quality = "tiny",
                fps = null,
                qualityLabel = null,
                averageBitrate = null,
                audioQuality = "AUDIO_QUALITY_MEDIUM",
                approxDurationMs = null,
                audioSampleRate = null,
                audioChannels = 2,
                loudnessDb = null,
                lastModified = null,
                signatureCipher = null,
                cipher = null,
                audioTrack = null
            ),
            streamUrl = "https://example.invalid/stream",
            streamExpiresInSeconds = 21_540
        )
    }
}
