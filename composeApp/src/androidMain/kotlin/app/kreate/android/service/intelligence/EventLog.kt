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
class Ulid {

    private val lastMillis = AtomicLong( 0 )
    private val counter = AtomicLong( 0 )

    fun generate( millis: Long, random: Random = Random.Default ): String {
        // Never let the visible timestamp go backwards within a process, and disambiguate events
        // that share a stamp with a counter rather than with luck.
        //
        // The two cases have to be handled together or the ids collide: when the clock *advances*
        // the stamp carries the ordering and the counter restarts, but when it does not advance —
        // whether because two events share a millisecond or because the clock moved backwards —
        // the counter is the only thing keeping them in order, so it must increment in both. An
        // earlier version reset it on a backwards jump, which gave two events the same sort key
        // and left their order down to the random tail.
        var seq = 0L
        val stamp = synchronized( this ) {
            val previous = lastMillis.get()
            if ( millis > previous ) {
                lastMillis.set( millis )
                counter.set( 0 )
                millis
            } else {
                seq = counter.incrementAndGet()
                previous
            }
        }

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

    companion object {
        private const val ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"   // Crockford base32

        /**
         * Shared generator for app code.
         *
         * Instance state rather than object state because the monotonic guard is *mutable process
         * state*: with it on an `object`, one test's clock left the counter somewhere that made a
         * later test's assertion depend on execution order. An order-dependent test is a flaky
         * test, so each [EventLog] owns its own generator and the tests get a fresh one each.
         */
        val shared = Ulid()
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
    private val sessions: SessionKeeper = SessionKeeper( clock ),
    private val ulid: Ulid = Ulid()
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
            id = ulid.generate( now ),
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
    private val gapMillis: Long = 30 * 60 * 1000L,
    private val ulid: Ulid = Ulid()
) {
    private var sessionId: String? = null
    private var lastActivity: Long = 0

    fun currentSessionId( now: Long = clock.nowMillis() ): String {
        val existing = sessionId
        // A backwards clock jump larger than the gap also starts a new session, which is the
        // honest reading: we cannot tell how much real time passed.
        val continuous = existing != null && kotlin.math.abs( now - lastActivity ) < gapMillis
        val id = if ( continuous ) existing!! else ulid.generate( now )
        sessionId = id
        lastActivity = now
        return id
    }

    fun reset() { sessionId = null; lastActivity = 0 }
}
