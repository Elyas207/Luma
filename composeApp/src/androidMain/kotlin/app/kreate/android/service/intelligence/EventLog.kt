package app.kreate.android.service.intelligence

import app.kreate.database.models.ListeningEvent
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/**
 * Identifiers for log rows.
 *
 * A ULID rather than a UUID because the log is read in order, and a ULID sorts by creation time as
 * a string. That means `ORDER BY ts, id` is a total order even when several events land in the same
 * millisecond, which happens constantly — a skip emits an end and a start together.
 *
 * The monotonic guard matters more than it looks: if the device clock jumps backwards, two events
 * can be issued with a *decreasing* timestamp, and without the counter the log would silently
 * reorder itself. Replay would then produce a different answer from the one the user actually got,
 * which defeats the point of having a replayable log at all.
 */
internal object Ulid {

    private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   // Crockford base32
    private val lastMillis = AtomicLong( 0 )
    private val counter = AtomicLong( 0 )

    fun generate( millis: Long, random: Random = Random.Default ): String {
        // Never let the visible timestamp go backwards within a process, and disambiguate events
        // that share a millisecond with a counter rather than with luck.
        val stamp = lastMillis.updateAndGet { previous ->
            if ( millis > previous ) millis else previous
        }
        val seq = if ( stamp == millis ) counter.incrementAndGet() else counter.getAndSet( 0 )

        val time = buildString {
            var remaining = stamp
            repeat( 10 ) {
                append( ENCODING[( remaining and 0x1F ).toInt()] )
                remaining = remaining shr 5
            }
        }.reversed()

        val tail = buildString {
            // 4 characters of sequence, then randomness, so same-millisecond events stay ordered.
            var s = seq
            repeat( 4 ) {
                append( ENCODING[( s and 0x1F ).toInt()] )
                s = s shr 5
            }
            repeat( 12 ) { append( ENCODING[random.nextInt( 32 )] ) }
        }

        return time + tail
    }
}

/**
 * Somewhere to put events. Storage is injected so the harness can run the whole engine in memory.
 */
interface EventSink {
    suspend fun append( event: ListeningEvent )
    suspend fun all(): List<ListeningEvent>
}

/** In-memory sink, used by the replay harness and the tests. */
class InMemoryEventSink : EventSink {
    private val events = mutableListOf<ListeningEvent>()
    override suspend fun append( event: ListeningEvent ) { events += event }
    override suspend fun all(): List<ListeningEvent> = events.toList()
    fun snapshot(): List<ListeningEvent> = events.toList()
    fun clear() = events.clear()
}

/**
 * The one way anything gets written to the log.
 *
 * Every method takes a [Provenance] and there is no overload that defaults it. That is deliberate
 * and slightly annoying at the call site, which is the point: a default would be chosen once,
 * silently, at the one call site where it mattered, and the resulting corruption is undetectable
 * after the fact.
 */
class EventLog(
    private val sink: EventSink,
    private val clock: LumaClock = LumaClock.System,
    private val sessions: SessionKeeper = SessionKeeper( clock )
) {

    suspend fun record(
        type: EventType,
        source: Provenance,
        itemId: String? = null,
        positionMs: Long? = null,
        durationMs: Long? = null,
        slot: Int? = null,
        context: String? = null
    ): ListeningEvent {
        val now = clock.nowMillis()
        val event = ListeningEvent(
            id = Ulid.generate( now ),
            ts = now,
            tzOffsetMinutes = clock.timezoneOffsetMinutes(),
            sessionId = sessions.currentSessionId( now ),
            type = type.wireName,
            itemId = itemId,
            positionMs = positionMs,
            durationMs = durationMs,
            source = source.wireName,
            slot = slot,
            context = context
        )
        sink.append( event )
        return event
    }

    suspend fun all(): List<ListeningEvent> = sink.all()
}

/**
 * Session boundaries.
 *
 * A session is activity with gaps under 30 minutes. Held here rather than in the model layer
 * because sessions are a property of *when things happened*, which is exactly what the log records;
 * the model derives everything else from the rows.
 */
class SessionKeeper(
    private val clock: LumaClock,
    private val gapMillis: Long = 30 * 60 * 1000L
) {
    private var sessionId: String? = null
    private var lastActivity: Long = 0

    fun currentSessionId( now: Long = clock.nowMillis() ): String {
        val existing = sessionId
        // A backwards clock jump larger than the gap also starts a new session, which is the
        // honest reading: we cannot tell how much real time passed.
        val continuous = existing != null && kotlin.math.abs( now - lastActivity ) < gapMillis
        val id = if ( continuous ) existing!! else Ulid.generate( now )
        sessionId = id
        lastActivity = now
        return id
    }

    fun reset() { sessionId = null; lastActivity = 0 }
}
