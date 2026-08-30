package app.kreate.android.service.taste

import app.kreate.android.Preferences
import app.kreate.database.models.ListeningSignal
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * What the app learns from how you actually listen.
 *
 * The whole design rests on one idea: **a skip is not a skip**. Leaving a track four seconds in is
 * a rejection; leaving it at 80% means you heard it and moved on. Treating those identically is
 * why most "smart" players feel stupid — they either never learn or learn the wrong thing.
 *
 * Three rules keep this honest, and they are the reason it can be shipped without being creepy:
 *
 * 1. **Reordering only.** Signals change what is *offered*. Nothing here removes anything from the
 *    library or hides it from search.
 * 2. **Three strikes.** No single action changes behaviour. A bad day, a mis-tap, or one song that
 *    didn't suit the moment must not teach the app anything.
 * 3. **Always explainable.** Every stored value renders as a sentence in the control centre. If a
 *    decision cannot be explained in words, it is not made.
 */
object TasteEngine {

    private val logger = Logger.withTag( "taste" )
    private val scope = CoroutineScope( SupervisorJob() + Dispatchers.IO )

    /** Below this fraction of a track, leaving counts as a rejection rather than a move-on. */
    private const val FAST_SKIP_FRACTION = 0.15f

    /** At or beyond this fraction, the track counts as heard even if not played to the very end. */
    private const val COMPLETION_FRACTION = 0.85f

    /** Starting the same song again within this window is a replay, not a fresh play. */
    private const val REPLAY_WINDOW_MS = 10 * 60 * 1000L

    /** Ids the recommender must not surface unprompted. Refreshed in the background. */
    @Volatile
    private var suppressed: Set<String> = emptySet()

    /** songId -> when it last finished, for replay detection. */
    private val recentlyFinished = ConcurrentHashMap<String, Long>()

    /** Learning can be switched off entirely; the user stays in charge. */
    private val isEnabled: Boolean
        get() = Preferences.TASTE_LEARNING_ENABLED.value

    fun refresh() {
        scope.launch {
            runCatching { Database.listeningSignalTable.suppressedIds().toSet() }
                .onSuccess { suppressed = it }
                .onFailure { logger.e( "failed to load suppressed ids", it ) }
        }
    }

    /** Whether the recommender should avoid offering [songId] unprompted. */
    fun isSuppressed( songId: String ): Boolean = songId in suppressed

    /**
     * The current suppression set, for callers filtering a batch.
     *
     * Reads the cached set rather than hitting the database: autoplay runs on the player's timing
     * and must not wait on IO to decide what to queue.
     */
    fun suppressedIdsSnapshot(): Set<String> = suppressed

    /**
     * Record how a song was left.
     *
     * @param positionMs where playback had reached
     * @param durationMs the song's length, or a non-positive value if unknown
     * @param wasManualSkip true when the user moved on themselves, rather than the track ending
     */
    fun recordDeparture(
        songId: String,
        positionMs: Long,
        durationMs: Long,
        wasManualSkip: Boolean
    ) {
        if ( !isEnabled || songId.isBlank() ) return

        // Without a duration there is no way to tell a rejection from a completion, and guessing
        // would poison the data. Silently doing nothing is the correct behaviour.
        if ( durationMs <= 0 ) return

        val fraction = ( positionMs.toFloat() / durationMs ).coerceIn( 0f, 1f )

        scope.launch {
            runCatching {
                val existing = Database.listeningSignalTable.find( songId )
                    ?: ListeningSignal( songId = songId )

                val now = System.currentTimeMillis()

                val updated = when {
                    !wasManualSkip && fraction >= COMPLETION_FRACTION -> {
                        val lastFinish = recentlyFinished.put( songId, now )
                        val isReplay = lastFinish != null && ( now - lastFinish ) < REPLAY_WINDOW_MS

                        if ( isReplay )
                            existing.copy( replays = existing.replays + 1, completions = existing.completions + 1 )
                        else
                            existing.copy( completions = existing.completions + 1 )
                    }

                    wasManualSkip && fraction < FAST_SKIP_FRACTION ->
                        existing.copy( fastSkips = existing.fastSkips + 1 )

                    wasManualSkip ->
                        existing.copy( lateSkips = existing.lateSkips + 1 )

                    // Ended early without a skip — a stream failure or a stop. Says nothing about
                    // taste, so it is deliberately not recorded.
                    else -> return@runCatching
                }.copy( updatedAt = now )

                Database.listeningSignalTable.upsert( updated )

                logger.d { "$songId -> score ${updated.score}, suppressed=${updated.isSuppressed}" }
            }.onFailure { logger.e( "failed to record departure for $songId", it ) }

            refresh()
        }
    }

    /** The user removed a song from the queue: deliberate, and the strongest ordinary rejection. */
    fun recordQueueRemoval( songId: String ) {
        if ( !isEnabled || songId.isBlank() ) return

        scope.launch {
            runCatching {
                val existing = Database.listeningSignalTable.find( songId )
                    ?: ListeningSignal( songId = songId )

                Database.listeningSignalTable.upsert(
                    existing.copy(
                        removals = existing.removals + 1,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }.onFailure { logger.e( "failed to record removal for $songId", it ) }

            refresh()
        }
    }

    /**
     * "I actually like this" — the user overriding the app.
     *
     * The counters are kept rather than cleared, so the control centre can still explain what was
     * observed; the override simply stops it being acted on. Wiping the history would make the
     * override look like it had erased something.
     */
    fun overrideAsLiked( songId: String ) {
        scope.launch {
            runCatching {
                val existing = Database.listeningSignalTable.find( songId ) ?: return@runCatching
                Database.listeningSignalTable.upsert(
                    existing.copy( userOverride = true, updatedAt = System.currentTimeMillis() )
                )
            }.onFailure { logger.e( "failed to override $songId", it ) }

            refresh()
        }
    }

    /** Forget everything learned about one song. */
    fun forget( songId: String ) {
        scope.launch {
            runCatching { Database.listeningSignalTable.forget( songId ) }
            refresh()
        }
    }

    /** Forget everything, everywhere. */
    fun forgetAll() {
        scope.launch {
            runCatching { Database.listeningSignalTable.forgetAll() }
            recentlyFinished.clear()
            refresh()
        }
    }
}
