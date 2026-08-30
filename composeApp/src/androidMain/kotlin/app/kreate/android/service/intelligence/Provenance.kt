package app.kreate.android.service.intelligence

/**
 * Why something played.
 *
 * The single most damaging silent bug this system can have is an autoplay-driven play recorded as
 * though the user chose it. Nothing downstream can detect it — the event looks identical — and the
 * consequence is that the model starts learning from its own recommendations and quietly collapses
 * onto whatever the ranker liked first.
 *
 * So this is an enum rather than a string at the call site, every playback entry point has to name
 * one, and the tests assert it specifically.
 */
enum class Provenance {

    /** The user picked it from a list they were browsing. The strongest statement of intent. */
    MANUAL_BROWSE,

    /** The user searched, then opened a result. Intent, and it names what they were looking for. */
    SEARCH,

    /** Played as part of a playlist the user opened. */
    PLAYLIST,

    /** The user had queued it themselves earlier — a deliberate future commitment. */
    QUEUE,

    /** The app chose it when the previous item ended. Discounted as evidence; see the weights. */
    AUTOPLAY,

    /** The app surfaced it as a recommendation and the user took it. Weaker than a browse. */
    RECOMMENDATION,

    /** Started from a notification or media button. */
    NOTIFICATION,

    /** Continuing something already in progress. Says nothing new about preference. */
    RESUME,

    /** Android Auto, a widget, or a voice command — the app cannot see how it was chosen. */
    EXTERNAL;

    val wireName: String get() = name.lowercase()

    /**
     * Whether a *positive* outcome under this provenance should be trusted at full weight.
     *
     * Passive acceptance is not endorsement: not skipping something the app chose is a much weaker
     * signal than choosing it. Negative signals are not discounted this way — a skip is a skip
     * whoever queued it, and arguably means *more* when the app chose badly.
     */
    val isUserChosen: Boolean
        get() = when ( this ) {
            MANUAL_BROWSE, SEARCH, PLAYLIST, QUEUE -> true
            AUTOPLAY, RECOMMENDATION, NOTIFICATION, RESUME, EXTERNAL -> false
        }

    companion object {
        fun fromWire( value: String? ): Provenance =
            entries.firstOrNull { it.wireName == value } ?: EXTERNAL
    }
}

/**
 * What happened.
 *
 * Stored as strings so a build that predates a new type reads the log without crashing — an
 * append-only log outlives the code that wrote it.
 */
enum class EventType {
    PLAY_START,
    PLAY_END,
    SKIP_NEXT,
    SKIP_PREV,
    SEEK,
    PAUSE,
    RESUME,
    /** The app went away mid-item; we never saw an end. */
    ABANDON,
    REPLAY,
    QUEUE_ADD,
    QUEUE_REMOVE,
    PLAYLIST_ADD,
    PLAYLIST_REMOVE,
    FAVOURITE,
    UNFAVOURITE,
    SEARCH,
    SEARCH_RESULT_OPEN,
    DOWNLOAD,
    DISLIKE;

    val wireName: String get() = name.lowercase()
}
