package app.kreate.android.service.intelligence

import app.kreate.android.Preferences
import app.kreate.database.models.ListeningEvent
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.Database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app-wide entry point to the personalisation layer.
 *
 * Thin on purpose: it owns wiring and nothing else. All the behaviour lives in [EventLog] and the
 * model built on top of it, both of which take their dependencies as constructor arguments so the
 * replay harness can run the identical code in memory with a fake clock.
 */
object Intelligence {

    private val logger = Logger.withTag( "intelligence" )
    private val scope = CoroutineScope( SupervisorJob() + Dispatchers.IO )

    /** Writes straight through to Room. */
    private val roomSink = object : EventSink {
        override suspend fun append( event: ListeningEvent ) {
            // insertIgnore, not upsert: the log is append-only, and a duplicate id means a retry
            // rather than a correction. Silently overwriting would let a replay rewrite history.
            runCatching { Database.listeningEventTable.insertIgnore( event ) }
                .onFailure { logger.e( "failed to append event", it ) }
        }
        override suspend fun all(): List<ListeningEvent> =
            runCatching { Database.listeningEventTable.allChronological() }.getOrDefault( emptyList() )
    }

    val log = EventLog( roomSink )

    /**
     * Learning is the user's to switch off, and switching it off means *recording nothing* rather
     * than recording quietly and ignoring it later. A log that keeps filling while the user believes
     * it is off is the kind of thing that destroys trust in a feature like this permanently.
     */
    private val isEnabled: Boolean
        get() = runCatching { Preferences.TASTE_LEARNING_ENABLED.value }.getOrDefault( true )

    /**
     * A private session writes **nothing**.
     *
     * Not "records and ignores later" — actually nothing, because a log that keeps filling while
     * the user believes it is off is the kind of discovery that ends trust in a feature like this
     * permanently. In memory rather than persisted, so it cannot be left on by accident across a
     * restart and quietly stop learning for weeks.
     */
    @Volatile
    var isPrivateSession: Boolean = false
        private set

    fun setPrivateSession( enabled: Boolean ) { isPrivateSession = enabled }

    /** "Forget the last 24 hours / 7 days / 30 days." Removes the rows, not just their effect. */
    fun forgetSince( windowMs: Long, clock: LumaClock = LumaClock.System, onDone: ( Int ) -> Unit = {} ) {
        scope.launch {
            val since = clock.nowMillis() - windowMs
            val removed = runCatching { Database.listeningEventTable.forgetSince( since ) }
                .onFailure { logger.e( "forget window failed", it ) }
                .getOrDefault( 0 )
            logger.d { "forgot $removed events from the last ${windowMs / 3_600_000}h" }
            onDone( removed )
        }
    }

    fun forgetEverything( onDone: () -> Unit = {} ) {
        scope.launch {
            runCatching { Database.listeningEventTable.forgetAll() }
                .onFailure { logger.e( "full reset failed", it ) }
            onDone()
        }
    }

    /**
     * Whether an event may be written at all.
     *
     * Extracted as a pure predicate so the guarantee can be asserted directly. Verifying it through
     * the UI proved unreliable — driving the toggle by text while playback was running kept landing
     * on the song menu instead — and "we could not reach the control" is not evidence that the
     * control works.
     */
    fun shouldRecord( learningEnabled: Boolean, privateSession: Boolean, itemId: String? ): Boolean =
        learningEnabled && !privateSession && !itemId.isNullOrBlank()

    fun record(
        type: EventType,
        itemId: String?,
        positionMs: Long? = null,
        durationMs: Long? = null,
        source: Provenance = PlaybackIntent.current()
    ) {
        if ( !shouldRecord( isEnabled, isPrivateSession, itemId ) ) return

        scope.launch {
            runCatching {
                log.record(
                    type = type,
                    source = source,
                    itemId = itemId,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            }.onFailure { logger.e( "failed to record ${type.wireName}", it ) }
        }
    }

    /**
     * Raw events live 90 days and are then hard-deleted.
     *
     * Derived affinity is not rebuilt from the log on a schedule — it carries its own decay — so
     * pruning does not quietly erase a preference the user still has. That separation is what makes
     * a short raw-retention window honest rather than a euphemism for keeping everything.
     */
    fun pruneExpired( clock: LumaClock = LumaClock.System ) {
        scope.launch {
            val cutoff = clock.nowMillis() - RETENTION_MS
            runCatching { Database.listeningEventTable.pruneBefore( cutoff ) }
                .onSuccess { if ( it > 0 ) logger.d { "pruned $it events older than 90 days" } }
                .onFailure { logger.e( "prune failed", it ) }
        }
    }

    const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
}
