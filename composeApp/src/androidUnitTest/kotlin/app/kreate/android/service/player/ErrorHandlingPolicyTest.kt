package app.kreate.android.service.player

import android.app.Application
import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.HttpDataSourceException
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Fallback eligibility decides whether a failed load abandons the current stream url and moves to
 * another source. Getting it wrong is expensive in both directions: falling back on a transient
 * network blip throws away a url that would have worked, and *not* falling back on a rejected url
 * retries something the CDN will never serve.
 */
// Robolectric is here only to give `Uri.parse` a real implementation. Pin a bare Application so it
// does not instantiate MainApplication, whose onCreate starts Koin — that succeeds for the first
// test and then throws KoinApplicationAlreadyStartedException for every one after it.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ErrorHandlingPolicyTest {

    private val policy = ErrorHandlingPolicy()

    private fun responseCode( code: Int ): InvalidResponseCodeException =
        InvalidResponseCodeException(
            code,
            /* responseMessage = */ null,
            /* cause = */ null,
            /* headerFields = */ emptyMap(),
            DataSpec( Uri.parse( "https://example.invalid/stream" ) ),
            /* responseBody = */ ByteArray( 0 )
        )

    @Test
    fun `missing resources fall back`() {
        // Re-requesting the same url cannot conjure a resource the CDN does not have
        for ( code in intArrayOf( 404, 410 ) )
            assertTrue( "expected fallback for HTTP $code", policy.isEligibleForFallback( responseCode( code ) ) )
    }

    @Test
    fun `a rejected url does not fall back, because it can be re-minted`() {
        // 403 mid-track is googlevideo retiring the url, not a verdict on the track: an un-attested
        // stream is served only up to a fixed fraction of the file and refused past it.
        // `ChunkedDataSource` drops the spent url on the way past so the retry re-resolves and
        // continues on a fresh one — abandoning the source here would turn a routine url rotation
        // into a dead track.
        assertFalse(
            "expected no fallback for HTTP 403",
            policy.isEligibleForFallback( responseCode( 403 ) )
        )
    }

    @Test
    fun `server-side failures do not fall back`() {
        // A 5xx is the server having a bad moment; the url itself is still valid
        for ( code in intArrayOf( 500, 502, 503, 504 ) )
            assertFalse( "expected no fallback for HTTP $code", policy.isEligibleForFallback( responseCode( code ) ) )
    }

    @Test
    fun `transport failures do not fall back`() {
        // The tunnel / dead-spot case. Retrying the same url is the cheaper recovery, and this is
        // exactly what the previous unconditional `return true` got wrong.
        val transport: List<IOException> = listOf(
            SocketTimeoutException( "timed out" ),
            UnknownHostException( "no dns" ),
            IOException( "connection reset" ),
            HttpDataSourceException(
                DataSpec( Uri.parse( "https://example.invalid/stream" ) ),
                HttpDataSourceException.TYPE_OPEN,
                HttpDataSourceException.TYPE_READ
            )
        )

        transport.forEach {
            assertFalse( "expected no fallback for ${it::class.simpleName}", policy.isEligibleForFallback( it ) )
        }
    }
}
