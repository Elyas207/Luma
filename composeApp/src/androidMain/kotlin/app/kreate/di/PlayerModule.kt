@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import app.kreate.android.Preferences
import app.kreate.android.service.DownloadHelper
import app.kreate.android.service.player.ErrorHandlingPolicy
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.service.player.StatefulPlayerImpl
import app.kreate.android.service.player.VolumeObserver
import app.kreate.android.utils.isLocalFile
import androidx.media3.common.util.UnstableApi
import it.fast4x.rimusic.utils.isVideo
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import me.knighthat.impl.DownloadHelperImpl
import me.knighthat.innertube.UserAgents
import okhttp3.OkHttpClient
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.minutes


const val CHUNK_LENGTH = 512 * 1024L     // 512KB

private const val CACHE_DIRNAME = "exo_cache"
private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

private fun initCache( context: Context, size: Long, cacheDirName: String ): Cache {
    val cacheEvictor = when( size ) {
        0L, Long.MAX_VALUE -> NoOpCacheEvictor()
        else -> LeastRecentlyUsedCacheEvictor( size )
    }
    val cacheDir = when( size ) {
        // Temporary directory deletes itself after close
        // It means songs remain on device as long as it's open
        0L -> createTempDirectory( cacheDirName ).toFile()

        // Looks a bit ugly but what it does is
        // check location set by user and return
        // appropriate path with [cacheDirName] appended.
        else -> when( Preferences.EXO_CACHE_LOCATION.value ) {
            ExoPlayerCacheLocation.System   -> context.cacheDir
            ExoPlayerCacheLocation.Private  -> context.filesDir
            ExoPlayerCacheLocation.SPLIT    -> if( cacheDirName == DOWNLOAD_CACHE_DIRNAME ) context.filesDir else context.cacheDir
        }.resolve( cacheDirName )
    }
    // Ensure this location exists
    cacheDir.mkdirs()

    return SimpleCache( cacheDir, cacheEvictor, StandaloneDatabaseProvider(context) )
}

/**
 * Wraps the normal source factory so a video item plays as one merged audio+video source.
 *
 * YouTube serves adaptive audio and video as separate streams. [MergingMediaSource] hands ExoPlayer
 * both, which is what lets a single player own video — inheriting the existing cache, the
 * MediaSession, correct position and duration, and the multi-client fallback, none of which the
 * old IFrame WebView had.
 *
 * The two halves share one resolution: the video load is keyed with [videoTrackKey], so it either
 * reuses the audio load's cached result or joins its in-flight sweep, costing no extra round trip.
 *
 * ponytail: a video track that fails takes the whole item down with it, because MergingMediaSource
 * has no partial mode. If that turns out to bite, the upgrade is to catch the video failure and
 * re-prepare audio-only rather than to make this factory cleverer.
 */
@UnstableApi
private class VideoAwareMediaSourceFactory(
    private val delegate: DefaultMediaSourceFactory
) : MediaSource.Factory by delegate {

    override fun createMediaSource( mediaItem: MediaItem ): MediaSource {
        val audio = delegate.createMediaSource( mediaItem )
        if ( !mediaItem.isVideo ) return audio

        val videoItem = mediaItem.buildUpon()
                                 .setUri( videoTrackKey( mediaItem.mediaId ) )
                                 .setCustomCacheKey( videoTrackKey( mediaItem.mediaId ) )
                                 .build()

        return MergingMediaSource( audio, delegate.createMediaSource( videoItem ) )
    }
}

/**
 * Buffering tuned for a car on mobile data rather than for a good connection.
 *
 * Two departures from the media3 defaults:
 * - A much deeper forward buffer. The default ~50s is sized for a network that stays up; a tunnel
 *   or a dead spot lasts longer than that, and audio is cheap enough to hold minutes of it.
 * - A real back-buffer, retained through the current position, so scrubbing backwards inside the
 *   track replays from memory instead of re-requesting bytes over a connection that may be gone.
 *
 * Playback still starts as promptly as before — the start/rebuffer thresholds are left low on
 * purpose, since the deep buffer is about surviving a drop-out, not about delaying the first note.
 */
private fun carFriendlyLoadControl(): LoadControl =
    DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 2.minutes.inWholeMilliseconds.toInt(),
            /* maxBufferMs = */ 5.minutes.inWholeMilliseconds.toInt(),
            /* bufferForPlaybackMs = */ 2_500,
            /* bufferForPlaybackAfterRebufferMs = */ 5_000
        )
        // Time, not size, decides how much we hold: a low-bitrate stream should still buy the
        // same number of seconds of resilience as a high-bitrate one.
        .setPrioritizeTimeOverSizeThresholds( true )
        .setBackBuffer(
            /* backBufferDurationMs = */ 1.minutes.inWholeMilliseconds.toInt(),
            /* retainBackBufferFromKeyframe = */ true
        )
        .build()

val playerModule = module {
    //<editor-fold desc="Cache">
    single( CacheType.CACHE ) {
        initCache( get(), Preferences.EXO_CACHE_SIZE.value, CACHE_DIRNAME )
    }
    single( CacheType.DOWNLOAD ) {
        initCache( get(), Preferences.EXO_DOWNLOAD_SIZE.value, DOWNLOAD_CACHE_DIRNAME )
    }
    factory( CacheType.CACHE ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.CACHE) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    factory( CacheType.DOWNLOAD ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.DOWNLOAD) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    //</editor-fold>

    single {
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                get(),
                // No factory-level User-Agent on purpose. `OkHttpDataSource` applies its own
                // `userAgent` *after* the DataSpec's headers, so setting one here would silently
                // overwrite the per-client agent that `resolveInnertubeMedia` attaches — and that
                // agent is what stops googlevideo returning 403 for urls minted by IOS/ANDROID
                // clients. Every remote request passes through the resolver, so each one arrives
                // carrying exactly one, correct User-Agent.
                // Ranged fetching sits *below* the resolver: the resolver decides which url,
                // this decides how it is transported. googlevideo refuses whole-file requests.
                ChunkedDataSource.Factory(
                    OkHttpDataSource.Factory(get<OkHttpClient>()),
                    onUrlSpent = ::invalidateStreamUrl
                )
            )
        ) { dataSpec ->
            if ( dataSpec.uri.isLocalFile() )
                // If this is a local file, no conversion needed
                // because its uri already points to a physical file
                dataSpec
            else
                resolveInnertubeMedia( dataSpec )
        }
    }

    // FIXME: This is technically usable but not recommended,
    //  new instance should be created on each injection.
    //  subscribers should use [PlaybackService]'s player instead of injecting
    //  an instance from Koin.
    // TODO: Convert this into factory
    single<StatefulPlayer> {
        //<editor-fold desc="DataSource">
        val dataSource = DefaultMediaSourceFactory(
            // At the bottom of the stack, it's download cache
            get<CacheDataSource.Factory>(CacheType.DOWNLOAD)
                // Read-only cache, player doesn't get to write anything in here
                .setCacheWriteDataSinkFactory( null )
                .setUpstreamDataSourceFactory(
                    // Next up is regular cache
                    get<CacheDataSource.Factory>(CacheType.CACHE)
                        // The final upstream handles 2 cases, local files and remote files
                        .setUpstreamDataSourceFactory( get<ResolvingDataSource.Factory>() )
                        // Player is allowed to write chunks into this storage.
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache( get(CacheType.CACHE) )
                                // Chunks are small so recovery can work better
                                .setFragmentSize( CHUNK_LENGTH )
                                // Bigger than default buffer size to avoid
                                // constant write to disk, but small enough
                                // to avoid data loss if app crashes
                                .setBufferSize( 64 * 1024 )     // 64KiB
                        )
                )
        )
        dataSource.setLoadErrorHandlingPolicy( ErrorHandlingPolicy() )
        val mediaSourceFactory = VideoAwareMediaSourceFactory( dataSource )
        //</editor-fold>
        //<editor-fold desc="Audio handlers">
        val handleAudioFocus by Preferences.AUDIO_SMART_PAUSE_DURING_CALLS
        val audioAttributes = AudioAttributes.Builder()
            .setUsage( C.USAGE_MEDIA )
            .setContentType( C.AUDIO_CONTENT_TYPE_MUSIC )
            .build()
        //</editor-fold>

        StatefulPlayerImpl(
            ExoPlayer.Builder( get() )
                .setMediaSourceFactory( mediaSourceFactory )
                .setLoadControl( carFriendlyLoadControl() )
                .setHandleAudioBecomingNoisy( true )
                .setWakeMode( C.WAKE_MODE_NETWORK )
                .setAudioAttributes( audioAttributes, handleAudioFocus )
                .setUsePlatformDiagnostics( false )
                .build()
        )
    }

    singleOf( ::VolumeObserver )
    singleOf( ::DownloadHelperImpl ) bind DownloadHelper::class
}

enum class CacheType : Qualifier {
    CACHE, DOWNLOAD;

    override val value: QualifierValue = toString().lowercase()
}