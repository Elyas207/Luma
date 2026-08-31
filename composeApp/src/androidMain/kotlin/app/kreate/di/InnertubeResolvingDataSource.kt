@file:kotlin.OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.Format
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import com.metrolist.music.utils.YTPlayerUtils
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.knighthat.innertube.Endpoints
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.UserAgents
import com.metrolist.innertube.models.response.PlayerResponse
import me.knighthat.utils.Toaster
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Store id of song just added to the database.
 * This is created to reduce load to Room
 */
private val justInserted = AtomicReference("")

/**
 * A resolved stream url plus the wall-clock instant it stops being usable.
 *
 * The deadline has to be stamped at resolve time: YouTube reports expiry as a duration from when
 * *it* minted the url, so the duration alone says nothing about whether the url is still good.
 */
internal class CachedStream(
    val data: YTPlayerUtils.PlaybackData,
    private val expiresAtMillis: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtMillis
}

/**
 * Absolute instant at which a url minted [expiresInSeconds] from [nowMillis] stops being usable.
 *
 * Kept separate so it can be asserted directly. The bug this replaces compared a *duration* to
 * epoch millis, which is true for every plausible input, so the cache never once hit.
 */
internal fun streamDeadlineMillis( nowMillis: Long, expiresInSeconds: Int ): Long =
    nowMillis + ( expiresInSeconds.seconds - EXPIRY_MARGIN ).inWholeMilliseconds

private val cachedStreamUrl = ConcurrentHashMap<String, CachedStream>()

/**
 * Resolutions currently running, keyed by video id, so a double-tap or a seek that re-triggers a
 * load joins the sweep already in progress instead of starting a second one.
 */
private val inFlightResolutions = ConcurrentHashMap<String, Deferred<YTPlayerUtils.PlaybackData>>()

/**
 * Resolution runs here rather than on ExoPlayer's load thread. That thread can then be released
 * the moment ExoPlayer abandons the load (a skip), while the sweep it started runs to completion
 * and populates [cachedStreamUrl] for whoever asks next.
 */
private val resolutionScope = CoroutineScope( SupervisorJob() + Dispatchers.IO )

/**
 * Backstop against a resolution that never returns — a full multi-client sweep with a PoToken
 * WebView round trip is legitimately slow on a bad connection, so this is deliberately generous.
 * It exists to bound the unbounded case, not to enforce a latency target.
 */
private val RESOLVE_TIMEOUT = 45.seconds

/**
 * Safety margin so a url that is about to lapse isn't handed to ExoPlayer moments before the CDN
 * starts refusing it.
 */
private val EXPIRY_MARGIN = 30.seconds

private val logger = Logger.withTag("dataspec")

/**
 * Acts as a lock to keep [upsertSongFormat] from starting before
 * [upsertSongInfo] finishes.
 */
private var databaseWorker: Job = Job()

//<editor-fold desc="Database handlers">
/**
 * Reach out to [Endpoints.NEXT] endpoint for song's information.
 *
 * Info includes:
 * - Titles
 * - Artist(s)
 * - Album
 * - Thumbnails
 * - Duration
 *
 * ### If song IS already inside database
 *
 * It'll replace unmodified columns with fetched data
 *
 * ### If song IS NOT already inside database
 *
 * New record will be created and insert into database
 *
 */
private fun upsertSongInfo( context: Context, videoId: String ) {       // Use this to prevent suspension of thread while waiting for response from YT
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() || !isNetworkAvailable( context ) )
        return

    logger.v { "fetching and upserting $videoId's information to the database" }

    databaseWorker = CoroutineScope(Dispatchers.IO ).launch {
        Innertube.songBasicInfo( videoId, CURRENT_LOCALE )
            .onSuccess{
                logger.v { "$videoId's information successfully found and parsed" }

                Database.upsert( it )

                logger.d { "$videoId's information successfully upserted to the database" }
            }
            .onFailure {
                logger.e( "failed to upsert $videoId's information to database", it )
                Toaster.e( R.string.error_failed_to_fetch_songs_info )
            }
    }

    // Must not modify [JustInserted] to [upsertSongFormat] let execute later
}

/**
 * Upsert provided format to the database
 */
private fun upsertSongFormat( videoId: String, format: PlayerResponse.StreamingData.Format ) {
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() ) return

    logger.v { "upserting format ${format.itag} of song $videoId to the database" }

    CoroutineScope(Dispatchers.IO ).launch {
        // Wait until this job is finish to make sure song's info
        // is in the database before continuing
        databaseWorker.join()

        Database.asyncTransaction {
            formatTable.upsert(
                Format(
                    videoId,
                    format.itag,
                    format.mimeType,
                    format.bitrate.toLong(),
                    format.contentLength,
                    format.lastModified,
                    format.loudnessDb?.toFloat()
                )
            )

            logger.d { "$videoId is successfully upserted to the database" }

            // Format must be added successfully before setting variable
            justInserted.store( videoId )
        }
    }
}
//</editor-fold>
/**
 * Run the full multi-client sweep for [songId] and cache the result against an absolute deadline.
 */
/**
 * A real visitor token is an opaque base64 blob minted by YouTube, always prefixed `Cg`.
 *
 * The app seeds [YouTube.visitorData] at startup from a preference whose default is a *browser
 * User-Agent string*, not a token. That is never null, so the "fetch one if we haven't got one"
 * check below never fired, and every request went out advertising
 * `visitorData: "Mozilla/5.0 (Windows NT 10.0…"`. YouTube reads that as an obviously forged
 * session and answers "Sign in to confirm you're not a bot" — which is a large part of playback
 * failing "a lot of the time".
 */
private fun String?.isRealVisitorToken(): Boolean =
    !isNullOrBlank() && startsWith( "Cg" ) && !contains( "Mozilla" )

/** False until this process has minted its own visitor token; never reset while it lives. */
private val visitorTokenMintedThisProcess = java.util.concurrent.atomic.AtomicBoolean( false )

/**
 * Make sure the session token we present was minted by *this* run.
 *
 * A visitor token is not valid forever, and the stored one was previously kept until it looked
 * malformed — so a token minted days ago went on being presented long after YouTube stopped
 * honouring it. An aged token is answered exactly like a forged one ("Sign in to confirm you're
 * not a bot"), which reads as an account demand and is nothing of the kind.
 *
 * Verified against the live service: the same VISIONOS player request that was refused with a
 * token lifted from an old log returned `OK` with 23 formats when given a freshly minted one, and
 * sending or omitting session cookies changed nothing either way. The token is the whole story.
 *
 * Minting is one request per process, so the cost is paid once at first play.
 */
private suspend fun ensureFreshVisitorToken() {
    val firstUseThisProcess = visitorTokenMintedThisProcess.compareAndSet( false, true )

    if( YouTube.visitorData.isRealVisitorToken() && !firstUseThisProcess ) return

    YouTube.visitorData()
           .onFailure { err ->
               // Keep whatever we had: a stale token still beats no token.
               logger.e( "failed to fetch visitorData", err )
           }
           .onSuccess {
               // Deliberately *not* persisted. `Preferences.setValue` is @MainThread and refuses a
               // write from anywhere else — it drops the value and raises an "unexpected error"
               // toast at the user — and this runs on ExoPlayer's load thread. Persisting it was
               // pointless besides: the token is re-minted every process, so a stored copy could
               // only ever be handed back staler than the one we just fetched.
               YouTube.visitorData = it
               logger.d { "Minted fresh visitorData for this session" }
           }
}

private suspend fun resolveStream( songId: String ): YTPlayerUtils.PlaybackData {
    ensureFreshVisitorToken()

    val connManager = get<Context>(Context::class.java).getSystemService<ConnectivityManager>()!!
    val audioQuality by Preferences.AUDIO_QUALITY

    val data = YTPlayerUtils.playerResponseForPlayback(
        videoId = songId,
        playlistId = null,
        audioQuality = audioQuality,
        connectivityManager = connManager
    ).getOrThrow()

    // Persist the format we actually resolved. Its loudnessDb is what volume normalisation reads,
    // so without this every streamed song normalises off a missing row.
    upsertSongFormat( songId, data.format )

    // Stamp the deadline now, while we know when the url was minted
    cachedStreamUrl[songId] = CachedStream(
        data,
        streamDeadlineMillis( System.currentTimeMillis(), data.streamExpiresInSeconds )
    )

    return data
}

private fun getPlayableUrl( songId: String ): YTPlayerUtils.PlaybackData {
    logger.v { "Processing $songId" }

    if( !CipherDeobfuscator.isInitialized() )
        CipherDeobfuscator.initialize( get(Context::class.java) )

    cachedStreamUrl[songId]?.let { cached ->
        if( !cached.isExpired ) {
            logger.d { "Stream url of $songId is cached" }
            return cached.data
        }

        logger.d { "Cached stream url of $songId expired" }
        cachedStreamUrl.remove( songId )
    }

    // Join an in-flight sweep for this id rather than starting a second one
    val resolution = inFlightResolutions.computeIfAbsent( songId ) { id ->
        resolutionScope.async { resolveStream( id ) }
                       .also { it.invokeOnCompletion { inFlightResolutions.remove( id ) } }
    }

    // Blocks the caller (ExoPlayer's load thread) but no longer *owns* the work: an interrupt
    // here — ExoPlayer giving up on this load — unwinds this wait and leaves [resolution] running.
    return runBlocking { withTimeout( RESOLVE_TIMEOUT ) { resolution.await() } }
}
//</editor-fold>

/**
 * Marks a cache key as addressing the video half of a track rather than the audio half.
 *
 * Audio and video are two separate loads of the *same* resolution, so they must not collide in the
 * disk cache — hence a distinct key — while still sharing one network sweep.
 */
const val VIDEO_TRACK_SUFFIX = ":video"

/**
 * Drop the cached stream url behind [cacheKey] so the next load mints a fresh one.
 *
 * A googlevideo url is not spent when its `expire` timestamp passes — it is spent after a small
 * number of range requests. Measured against a live `c=IOS` url: ranges at offset 0, 256 KB and
 * 512 KB returned 206 and every range from then on returned 403, *including a re-request of byte
 * zero*, roughly six hours before the `expire` the url itself advertises.
 *
 * [cachedStreamUrl] keys on that advertised expiry alone, so without this a mid-track 403 is
 * unrecoverable: ExoPlayer does retry the load, but each retry re-enters the resolver, hits the
 * still-"valid" cache entry and is handed the same dead url back. Observed as seventeen 403s in a
 * row and a "Source error" the moment the buffer drained — about 57 seconds into a four-minute
 * track, which is exactly the audio the last good range had bought.
 */
fun invalidateStreamUrl( cacheKey: String ) {
    val songId = cacheKey.removeSuffix( VIDEO_TRACK_SUFFIX )

    if ( cachedStreamUrl.remove( songId ) != null )
        logger.d { "Stream url of $songId was rejected; dropped so the next load re-resolves" }
}

/** Cache key addressing the video track of [songId]. */
fun videoTrackKey( songId: String ): String = songId + VIDEO_TRACK_SUFFIX

fun Scope.resolveInnertubeMedia( dataSpec: DataSpec ): DataSpec {
    val cacheKey = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }

    val wantsVideo = cacheKey.endsWith( VIDEO_TRACK_SUFFIX )
    val songId = cacheKey.removeSuffix( VIDEO_TRACK_SUFFIX )

    upsertSongInfo( get(), songId )

    // Both halves resolve through the same call, so the video load either reuses the audio load's
    // cached result or joins its in-flight sweep. One network round trip serves both.
    val cache = getPlayableUrl( songId )

    val format = if ( wantsVideo ) cache.videoFormat else cache.format
    val streamUrl = if ( wantsVideo ) cache.videoStreamUrl else cache.streamUrl

    // A video track that resolved to nothing must fail loudly here rather than silently handing
    // ExoPlayer the audio url, which would look like a corrupt video stream.
    requireNotNull( streamUrl ) { "No video stream available for $songId" }

    /*
     * The full length is reported here on purpose. Splitting the fetch into ranges is the
     * transport's job — see [ChunkedDataSource] — because a short length here makes
     * `ProgressiveMediaPeriod` believe the track itself is that short and raise `EOFException` at
     * the end of the first range.
     */
    return dataSpec.buildUpon()
                   .setUri( streamUrl )
                   .setLength( format?.contentLength ?: C.LENGTH_UNSET.toLong() )
                   // Play it as whoever asked for it. See [userAgentFor].
                   .setHttpRequestHeaders(
                       dataSpec.httpRequestHeaders + ( "User-Agent" to userAgentFor( cache.streamClient ) )
                   )
                   .build()
}

/**
 * The User-Agent that matches the client a stream url was minted for.
 *
 * googlevideo binds a url to the identity that requested it. The whole multi-client fallback exists
 * to find a client YouTube will actually serve — and then the player was handing every url it found
 * to a `DataSource` pinned to `CHROME_WINDOWS`, so a url obtained as `c=IOS` was fetched with a
 * Chrome-on-Windows header and refused with a 403.
 *
 * This was the last hop of the "doesn't work a lot of the time" loop and it hid well, because every
 * layer above it looked correct: the client sweep succeeded, the url validated, and only ExoPlayer's
 * own request failed. Reproduced directly — a url that returned `206` to a ranged GET carrying the
 * iOS agent returned `403` to ExoPlayer moments later carrying Chrome's.
 *
 * Unknown clients fall back to Chrome, which is what every request used to send, so an unrecognised
 * name can only ever be as wrong as the old behaviour.
 */
private fun userAgentFor( streamClient: String ): String = when {
    streamClient.startsWith( "IOS" ) || streamClient.startsWith( "IPADOS" ) -> UserAgents.IOS
    streamClient.startsWith( "ANDROID_VR" )                                 -> UserAgents.ANDROID_VR
    streamClient.startsWith( "ANDROID" )                                    -> UserAgents.ANDROID
    streamClient == "TVHTML5_SIMPLY_EMBEDDED_PLAYER"                        -> UserAgents.TVHTML5_SIMPLY_EMBEDDED_PLAYER
    else                                                                    -> UserAgents.CHROME_WINDOWS
}

/**
 * Remove cached url of [songId].
 *
 * @return `true` if song's url was cached, and is deleted, `false` otherwise.
 */
fun clearCachedStreamUrlOf( songId: String ): Boolean =
    cachedStreamUrl.remove( songId ) != null
