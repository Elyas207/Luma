package app.kreate.android.service.intelligence

import java.util.concurrent.atomic.AtomicReference

/**
 * Why the thing that is about to play, is about to play.
 *
 * ### Why this is ambient rather than a parameter
 *
 * Provenance logically belongs as an argument to every "start playing" call. In this codebase that
 * is 77 call sites across `forcePlay`, `forcePlayAtIndex`, `playNext` and `enqueue`, and threading a
 * new required parameter through all of them by hand — with no test coverage over playback — is a
 * large change with a lot of places to get it quietly wrong. So the surface that *causes* playback
 * declares its intent just before it acts, and the log reads it at the moment it records.
 *
 * ### The failure direction is chosen deliberately
 *
 * The obvious risk is a surface forgetting to declare, leaving a stale value to be attributed to
 * whatever plays next. Two things bound that:
 *
 * 1. A declaration **expires** ([VALIDITY_MS]). Playback that starts long after the last user
 *    action was not caused by that action.
 * 2. An expired or absent declaration reads as [Provenance.EXTERNAL], which is *not* user-chosen.
 *
 * So the worst case is that a genuine user choice is recorded as "we don't know", which under-counts
 * evidence. The failure that actually matters — an app-chosen play recorded as a user choice, which
 * teaches the model from its own output and cannot be detected afterwards — cannot happen by
 * omission. It can only happen if a surface declares the wrong thing on purpose.
 */
object PlaybackIntent {

    /**
     * How long a declaration stays valid.
     *
     * Long enough to cover resolving a stream and starting playback on a slow connection (observed
     * at 4-6s in this app), short enough that the next autoplay ten minutes later cannot inherit it.
     */
    private const val VALIDITY_MS = 15_000L

    private data class Declared( val provenance: Provenance, val atMillis: Long )

    private val current = AtomicReference<Declared?>( null )

    @Volatile
    private var clock: LumaClock = LumaClock.System

    /** For tests. */
    fun useClock( replacement: LumaClock ) { clock = replacement }

    /**
     * "The user just did X, and playback is about to follow from it."
     *
     * Call immediately before the call that starts playback, not after.
     */
    fun declare( provenance: Provenance ) {
        current.set( Declared( provenance, clock.nowMillis() ) )
    }

    /**
     * What caused the playback happening right now, if it is still fresh enough to have caused it.
     */
    fun current(): Provenance {
        val declared = current.get() ?: return Provenance.EXTERNAL
        val age = clock.nowMillis() - declared.atMillis
        // A negative age means the clock moved backwards; treat that as unknown rather than valid.
        return if ( age in 0..VALIDITY_MS ) declared.provenance else Provenance.EXTERNAL
    }

    // ------------------------------------------------------------------ per-item attribution

    /**
     * Which provenance each currently-known item started under.
     *
     * Reading [current] at *departure* time was wrong, and the device found it: a track chosen from
     * search and played for 77 seconds recorded its skip as `external`, because the declaration had
     * aged out while the track was still playing. Provenance answers "why did this item start",
     * which does not stop being true as the item plays.
     *
     * So the declaration is snapshotted against the item when it becomes current, and every later
     * event about that item reads the snapshot. Bounded so a long queue cannot grow it without end.
     */
    private const val MAX_ATTRIBUTED = 64
    private val attributed = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Provenance>( 16, 0.75f, false ) {
            override fun removeEldestEntry( eldest: Map.Entry<String, Provenance>? ) = size > MAX_ATTRIBUTED
        }
    )

    /**
     * Record why [itemId] started, using whatever the surface declared. Call when an item becomes
     * the current one.
     */
    fun attribute( itemId: String ) {
        if ( itemId.isBlank() ) return
        attributed[itemId] = current()
    }

    /** Why [itemId] started, or EXTERNAL if we genuinely do not know. */
    fun provenanceFor( itemId: String ): Provenance =
        attributed[itemId] ?: Provenance.EXTERNAL

    /** Explicitly hand back to "the app is driving" — call when autoplay takes over. */
    fun clear() = current.set( null )

    /** Test seam. */
    fun resetForTest() { current.set( null ); attributed.clear() }
}
