package app.kreate.android.service.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.io.IOException

@UnstableApi
class ErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    /**
     * Whether ExoPlayer should abandon this source and try another one.
     *
     * The previous blanket `true` treated a flaky connection the same as a rejected url, so a
     * single timeout in a tunnel burned the fallback rather than retrying the load that would
     * have succeeded a moment later.
     *
     * Falling back is right only when re-requesting the resource cannot help: the CDN has no such
     * resource (404) or it is gone (410). Everything else — timeouts, resets, DNS failures, 5xx —
     * is transient, so retrying is the cheaper and more likely recovery, and the caller's own
     * retry budget still bounds it.
     *
     * 403 is deliberately *not* in that set. A googlevideo url is retired after a handful of range
     * requests, long before its advertised expiry, so mid-track it means "this url is used up",
     * not "this track is unavailable". `ChunkedDataSource` drops the spent url on the way past, so
     * the retry re-resolves and continues on a fresh one; abandoning the source instead would turn
     * an ordinary url rotation into a dead track.
     */
    public override fun isEligibleForFallback( exception: IOException ): Boolean =
        exception is InvalidResponseCodeException
                && exception.responseCode in FALLBACK_RESPONSE_CODES

    private companion object {
        val FALLBACK_RESPONSE_CODES = setOf( 404, 410 )
    }
}
