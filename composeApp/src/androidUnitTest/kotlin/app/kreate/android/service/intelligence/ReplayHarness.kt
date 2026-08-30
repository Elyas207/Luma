package app.kreate.android.service.intelligence

import app.kreate.database.models.ListeningEvent

/**
 * Test infrastructure for the personalisation layer.
 *
 * Two halves: a way to *write* a plausible listening history, and a way to *replay* one and see
 * what the model concluded. Both exist because the alternative — reasoning about whether a weight
 * change is an improvement — is guessing. Everything here runs on a [TestClock], so a 90-day
 * suppression expiry is a line of code rather than a wait.
 *
 * The harness deliberately drives the *same* classes the app does. If it reimplemented the
 * arithmetic it would be measuring a system no user ever runs.
 */

/** Minimal catalogue entry — the facets the model actually keys off. */
data class TestItem(
    val id: String,
    val creator: String,
    val contentClass: ContentClass = ContentClass.UNKNOWN,
    val durationMs: Long = 200_000
)

/**
 * Builds a synthetic history.
 *
 * Time advances automatically as events are added, so a scenario reads as a story rather than as a
 * pile of timestamps: `played(x).then(skipped(y))`.
 */
class SyntheticLog( startMillis: Long = 1_700_000_000_000 ) {

    val clock = TestClock( startMillis )
    private val ulid = Ulid()
    private val sessions = SessionKeeper( clock )
    private val events = mutableListOf<ListeningEvent>()

    private fun add(
        type: EventType,
        item: TestItem,
        source: Provenance,
        positionMs: Long,
        elapseMs: Long
    ): SyntheticLog {
        val now = clock.nowMillis()
        events += ListeningEvent(
            id = ulid.generate( now ),
            ts = now,
            tzOffsetMinutes = clock.timezoneOffsetMinutes(),
            sessionId = sessions.currentSessionId( now ),
            type = type.wireName,
            itemId = item.id,
            positionMs = positionMs,
            durationMs = item.durationMs,
            source = source.wireName
        )
        clock.advanceBy( elapseMs )
        return this
    }

    /** Listened to the end. */
    fun completed( item: TestItem, source: Provenance = Provenance.MANUAL_BROWSE ) =
        add( EventType.PLAY_END, item, source, item.durationMs, item.durationMs )

    /** Left it early — the position is what makes this a rejection rather than a move-on. */
    fun skipped( item: TestItem, atMs: Long = 8_000, source: Provenance = Provenance.AUTOPLAY ) =
        add( EventType.SKIP_NEXT, item, source, atMs, atMs )

    fun favourited( item: TestItem ) =
        add( EventType.FAVOURITE, item, Provenance.MANUAL_BROWSE, 0, 1_000 )

    fun replayed( item: TestItem, source: Provenance = Provenance.MANUAL_BROWSE ) =
        add( EventType.REPLAY, item, source, 0, item.durationMs )

    fun queued( item: TestItem ) =
        add( EventType.QUEUE_ADD, item, Provenance.QUEUE, 0, 1_000 )

    /** Time passes with nothing happening. */
    fun idle( days: Double ) = apply { clock.advanceDays( days ) }

    fun events(): List<ListeningEvent> = events.toList()
    fun nowMillis(): Long = clock.nowMillis()
}

/** What a replay concluded. */
data class ReplayResult(
    val model: AffinityModel,
    val atMillis: Long,
    val itemsById: Map<String, TestItem>
) {
    fun creatorEffect( creator: String ): Double =
        model.effect( FacetType.CREATOR, creator, atMillis )

    fun classEffect( contentClass: ContentClass ): Double =
        model.effect( FacetType.CONTENT_CLASS, contentClass.name, atMillis )

    fun itemEffect( itemId: String ): Double =
        model.effect( FacetType.ITEM, itemId, atMillis )
}

/**
 * Replays a log into a model.
 *
 * One pass, in order, applying exactly what the app applies: base reading, then modifiers, then
 * attribution across the item's facets. Deterministic — the same log always produces the same
 * model, which is what makes a weight change measurable as a diff.
 */
object ReplayHarness {

    fun replay(
        events: List<ListeningEvent>,
        catalogue: List<TestItem>,
        atMillis: Long,
        isCarMode: Boolean = false,
        skipStreakFrom: Int = Int.MAX_VALUE
    ): ReplayResult {
        val byId = catalogue.associateBy { it.id }
        val model = AffinityModel()
        var consecutiveSkips = 0

        for ( event in events.sortedWith( compareBy( { it.ts }, { it.id } ) ) ) {
            val item = event.itemId?.let( byId::get ) ?: continue

            val isSkip = event.type == EventType.SKIP_NEXT.wireName
            consecutiveSkips = if ( isSkip ) consecutiveSkips + 1 else 0

            val evidence = EvidenceExtractor.modified(
                event = event,
                isCarMode = isCarMode,
                contentClass = item.contentClass,
                sessionSkipStreak = consecutiveSkips >= skipStreakFrom
            )
            if ( evidence.weight <= 0f ) continue

            // Attribution: every facet of the item learns from the event, scaled by how much that
            // facet is worth knowing about.
            model.observe( FacetType.CREATOR, item.creator, evidence, event.ts )
            model.observe( FacetType.CONTENT_CLASS, item.contentClass.name, evidence, event.ts )
            model.observe( FacetType.ITEM, item.id, evidence, event.ts )
        }

        return ReplayResult( model, atMillis, byId )
    }
}

/** Reusable archetypes, so scenarios describe a person rather than a list of rows. */
object Archetypes {

    val mishary = TestItem( "q1", "Mishary", ContentClass.QURAN, 2_400_000 )
    val misharyTwo = TestItem( "q2", "Mishary", ContentClass.QURAN, 1_800_000 )
    val sudais = TestItem( "q3", "Sudais", ContentClass.QURAN, 2_000_000 )
    val nasheedA = TestItem( "n1", "Siedd", ContentClass.NASHEED, 200_000 )
    val nasheedB = TestItem( "n2", "Siedd", ContentClass.NASHEED, 210_000 )
    val lecture = TestItem( "l1", "Speaker", ContentClass.LECTURE, 3_600_000 )

    val catalogue = listOf( mishary, misharyTwo, sudais, nasheedA, nasheedB, lecture )

    /** Listens to recitation and nothing else. */
    fun quranOnly(): SyntheticLog = SyntheticLog().apply {
        repeat( 6 ) {
            completed( mishary )
            idle( 0.5 )
            completed( misharyTwo )
            idle( 0.5 )
        }
    }

    /** Brand new — no history at all. */
    fun brandNew(): SyntheticLog = SyntheticLog()

    /** Nothing suits them today; they skip everything. */
    fun chaoticSkipper(): SyntheticLog = SyntheticLog().apply {
        listOf( nasheedA, nasheedB, lecture, nasheedA, nasheedB ).forEach { skipped( it ) }
    }

    /** Loves a creator, then skips three of their new uploads. */
    fun lovedThenSkipped(): SyntheticLog = SyntheticLog().apply {
        repeat( 10 ) { completed( nasheedA ); idle( 0.4 ) }
        favourited( nasheedA )
        repeat( 3 ) { skipped( nasheedB ); idle( 0.2 ) }
    }
}
