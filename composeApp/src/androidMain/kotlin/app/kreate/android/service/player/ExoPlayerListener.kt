package app.kreate.android.service.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.widget.Toast
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexed
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.taste.TasteEngine
import app.kreate.database.models.PersistentQueue
import app.kreate.di.clearCachedStreamUrlOf
import com.metrolist.music.utils.YTPlayerUtils
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NotificationButtons
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MissingDecipherKeyException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.playNext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalAtomicApi::class)
@UnstableApi
class ExoPlayerListener(
    private val player: StatefulPlayer,
    private val mediaSession: MediaSession,
    private val waitingForNetwork: MutableStateFlow<Boolean>,
    private val sendOpenEqualizerIntent: () -> Unit,
    private val sendCloseEqualizerIntent: () -> Unit,
    private val onMediaTransition: (MediaItem?) -> Unit
): Player.Listener, KoinComponent {

    private val context: Context by inject()

    private var volumeNormalizationJob: Job = Job()
    private var errorTimestamp = 0L
    private var lastErrorMessage = ""

    /**
     * Media id whose stream URL has already been invalidated and re-resolved once for the current
     * error. Guards against a re-prepare loop when the fresh URL fails the same way. Cleared as
     * soon as anything reaches [Player.STATE_READY], so a later session-expiry on the same song
     * still gets its own recovery attempt.
     */
    private var recoveryAttemptedFor: String? = null

    var loudnessEnhancer: LoudnessEnhancer? = null
        private set

    /**
     * Checkpoints the playhead while a track is playing.
     *
     * The queue and position were only written on play/pause and on timeline changes, which means
     * that during ordinary playback nothing is saved at all. Kill the process three minutes into a
     * recitation — which Android does routinely — and the stored position is still whatever it was
     * when playback started, so the app reopens on "Nothing playing yet" and the user starts again
     * from the beginning. On a two-hour recitation that is the difference between the feature
     * working and not existing.
     *
     * Twenty seconds is the trade: frequent enough that almost nothing is lost, rare enough that it
     * costs a transaction every twenty seconds rather than every frame. Cancelled the moment
     * playback stops, so a paused or idle app does no work at all.
     */
    private var checkpointJob: kotlinx.coroutines.Job? = null

    override fun onIsPlayingChanged( isPlaying: Boolean ) {
        checkpointJob?.cancel()
        if ( !isPlaying ) {
            // Leaving playback is itself worth recording, and it is the last chance to do so.
            saveQueueToDatabase()
            return
        }

        checkpointJob = CoroutineScope( Dispatchers.Default ).launch {
            while ( isActive ) {
                kotlinx.coroutines.delay( 20_000 )
                saveQueueToDatabase()
            }
        }
    }

    /**
     * Requires [Preferences.ENABLE_PERSISTENT_QUEUE] to be **enabled** to work.
     */
    @AnyThread
    fun saveQueueToDatabase() {
        if( !Preferences.ENABLE_PERSISTENT_QUEUE.value ) return

        CoroutineScope( Dispatchers.Default ).launch {
            val (queue, index, playerPos) = withContext(Dispatchers.Main ) {
                // Any call related to [Player] must happen on main thread
                with( player ) {
                    Triple(currentTimeline.mediaItems, currentMediaItemIndex, currentPosition)
                }
            }
            if( queue.isEmpty() ) return@launch

            val queueItems = queue.fastMapIndexed { i, m ->
                PersistentQueue(
                    songId = m.mediaId,
                    position = if( i == index ) playerPos else null
                )
            }
            Database.asyncTransaction {
                queueTable.deleteAll()
                queue.forEach( ::insertIgnore )
                queueTable.insertIgnore( queueItems )
            }
        }
    }

    /**
     * (Re)render media control in notification area.
     */
    @AnyThread
    fun updateMediaControl( context: Context, player: Player ) {
        CoroutineScope(Dispatchers.Default ).launch {
            var firstButton: CommandButton? = null
            var secondButton: CommandButton? = null
            val buttons = mutableListOf<CommandButton>()

            NotificationButtons.entries
                .fastMap { it to PlaybackController.makeButton( context, player, it ) }
                .fastForEach { (nBtn, cmdBtn) ->
                    when (nBtn) {
                        Preferences.MEDIA_NOTIFICATION_FIRST_ICON.value -> firstButton = cmdBtn
                        Preferences.MEDIA_NOTIFICATION_SECOND_ICON.value -> secondButton = cmdBtn
                        else -> buttons.add( cmdBtn )
                    }
                }

            val layoutButton = buildList {
                firstButton?.also( ::add )
                secondButton?.also( ::add )
                addAll( buttons )
            }

            withContext( Dispatchers.Main ) {
                mediaSession.setMediaButtonPreferences( layoutButton )
            }
        }
    }

    private fun loadFromRadio( reason: Int ) {
        // Don't fetch more item if:
        // - Feature is disabled
        // - When song is repeated
        // - Start new queue
        if( !Preferences.QUEUE_AUTO_APPEND.value
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
        ) return

        val positionToLast = player.mediaItemCount - player.currentMediaItemIndex
        // Make sure only add when about 10 songs to the last song in queue
        // TODO: Add slider in settings to let user change number of songs
        if( positionToLast <= 10 && !player.isLoadingRadio() )
            player.startRadio()
    }

    @MainThread
    private fun traverseErrorStack( t: Throwable ): Throwable =
        when( t ) {
            is PlayableFormatNotFoundException,
            is UnplayableException,
            is LoginRequiredException,
            is NoInternetException,
            is UnknownException,
            is MissingDecipherKeyException -> t

            else -> t.cause?.let( ::traverseErrorStack ) ?: t
        }

    /**
     * HTTP status of the failed load, or `null` when the failure wasn't an HTTP response.
     *
     * Walks the whole cause chain rather than reusing [traverseErrorStack], which stops early on
     * the app's own exception types and would hide the response code underneath them.
     */
    private fun findResponseCode( t: Throwable? ): Int? = when( t ) {
        null -> null
        is InvalidResponseCodeException -> t.responseCode
        else -> findResponseCode( t.cause )
    }

    /**
     * A resolved CDN url that answers 403/410 is expired or was minted for a client YouTube has
     * since rejected. Both are recoverable: drop the cached url so the next resolve refetches, and
     * stop [YTPlayerUtils] handing back the same unvalidated `WEB_REMIX` url it just gave us.
     *
     * @return `true` when recovery was started, so the caller must not skip the track
     */
    @MainThread
    private fun tryRecoverStream( error: PlaybackException ): Boolean {
        val responseCode = findResponseCode( error ) ?: return false
        if( responseCode != 403 && responseCode != 410 ) return false

        val mediaId = player.currentMediaItem?.mediaId ?: return false
        // Already re-resolved this one and it still fails — let the caller skip instead of looping
        if( recoveryAttemptedFor == mediaId ) return false

        recoveryAttemptedFor = mediaId
        clearCachedStreamUrlOf( mediaId )
        YTPlayerUtils.markWebRemixFailed( mediaId )
        player.prepare()

        return true
    }

    @MainThread
    private fun printErrorMessage( errMsg: String )  {
        // If the same error is set within 10s, it'll be ignored.
        val timeWindow = errorTimestamp + 10.seconds.inWholeMilliseconds

        if( errMsg == lastErrorMessage
            && System.currentTimeMillis() <= timeWindow
        ) return

        lastErrorMessage = errMsg
        // When field is successfully set, update timestamp.
        errorTimestamp = System.currentTimeMillis()
        // Finally, print the error if not blank
        if( errMsg.isNotBlank() )
            Toaster.e( errMsg, Toast.LENGTH_LONG )
    }

    override fun onPlayWhenReadyChanged( playWhenReady: Boolean, reason: Int ) = saveQueueToDatabase()

    override fun onRepeatModeChanged( repeatMode: Int ) {
        updateMediaControl( context, this.player )
        Preferences.QUEUE_LOOP_TYPE.value = QueueLoopType.from( repeatMode )
    }

    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        if ( player.playerError != null ) player.prepare()

        // Bind why this item started to the item itself, at the moment it becomes current. Reading
        // the ambient declaration later would attribute a long track's own skip to "unknown",
        // because the declaration ages out while the track is still playing.
        mediaItem?.mediaId?.let { id ->
            if ( reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO )
                // The app advanced by itself. Say so explicitly rather than letting the previous
                // declaration age out into "unknown": both are safe, but only one is informative,
                // and autoplay is the provenance whose evidence has to be discounted by name.
                app.kreate.android.service.intelligence.PlaybackIntent.declare(
                    app.kreate.android.service.intelligence.Provenance.AUTOPLAY
                )

            app.kreate.android.service.intelligence.PlaybackIntent.attribute( id )

            app.kreate.android.service.intelligence.Intelligence.record(
                type = app.kreate.android.service.intelligence.EventType.PLAY_START,
                itemId = id,
                source = app.kreate.android.service.intelligence.PlaybackIntent.provenanceFor( id )
            )
        }

        loadFromRadio(reason)
        onMediaTransition( mediaItem )
    }

    /**
     * Report how the outgoing track was left to [TasteEngine].
     *
     * This callback rather than [onMediaItemTransition] because it is the only one that carries the
     * *old* position: by the time a transition is reported the playhead has already moved, and
     * "how far in did they get" is precisely the thing the learning depends on.
     *
     * The reason also separates intent cleanly — `AUTO_TRANSITION` means the track ran out on its
     * own, `SEEK` means something deliberately moved to another item, which in this app means the
     * user skipped.
     */
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if ( reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
             reason != Player.DISCONTINUITY_REASON_SEEK
        ) return

        // A seek *within* a track says nothing about whether the user liked it.
        if ( oldPosition.mediaItemIndex == newPosition.mediaItemIndex ) return

        val departing = oldPosition.mediaItem ?: return

        val duration = runCatching {
            player.currentTimeline
                  .getWindow( oldPosition.mediaItemIndex, Timeline.Window() )
                  .durationMs
        }.getOrDefault( C.TIME_UNSET )

        val wasManualSkip = reason == Player.DISCONTINUITY_REASON_SEEK

        TasteEngine.recordDeparture(
            songId = departing.mediaId,
            positionMs = oldPosition.positionMs,
            durationMs = duration,
            wasManualSkip = wasManualSkip
        )

        // The same departure, recorded as evidence rather than as a counter. This callback is the
        // right place for both: it is the only one carrying the *old* position, and the reason code
        // already separates "the track ran out" from "something moved off it deliberately".
        app.kreate.android.service.intelligence.Intelligence.record(
            type = if ( wasManualSkip )
                       app.kreate.android.service.intelligence.EventType.SKIP_NEXT
                   else
                       app.kreate.android.service.intelligence.EventType.PLAY_END,
            itemId = departing.mediaId,
            positionMs = oldPosition.positionMs,
            durationMs = duration.takeIf { it != C.TIME_UNSET },
            // The provenance this item *started* under, not whatever is ambient now.
            source = app.kreate.android.service.intelligence.PlaybackIntent
                        .provenanceFor( departing.mediaId )
        )
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if ( reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED )
            saveQueueToDatabase()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateMediaControl( context, this.player )
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] = shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlayerError( error: PlaybackException ) {
        // Recover silently where possible — a stale url that re-resolves is not worth a toast,
        // and skipping the track the user just picked is the worst possible answer to it.
        if ( tryRecoverStream( error ) ) return

        val rootCause = traverseErrorStack( error )

        when( rootCause ) {
            is PlayableFormatNotFoundException -> context.getString( R.string.error_couldn_t_find_a_playable_audio_format )
            is NoInternetException -> context.getString( R.string.no_connection )
            is MissingDecipherKeyException -> context.getString( R.string.error_failed_to_decipher_signature )

            else -> rootCause.message ?: context.getString( R.string.error_unknown )
        }.also( ::printErrorMessage )

        if ( Preferences.PLAYBACK_SKIP_ON_ERROR.value && player.hasNextMediaItem() )
            player.playNext()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            // Anything that reaches READY proves the pipeline recovered, so re-arm recovery for
            // the next failure instead of leaving it latched to the id that last failed.
            if ( player.playbackState == Player.STATE_READY )
                recoveryAttemptedFor = null

            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
                if (!player.playWhenReady) {
                    waitingForNetwork.value = false
                }
            }
        }
    }
}