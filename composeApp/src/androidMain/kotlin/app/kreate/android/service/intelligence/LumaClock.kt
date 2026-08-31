package app.kreate.android.service.intelligence

import java.util.TimeZone

/**
 * Where time comes from.
 *
 * Every part of the personalisation layer is a function of elapsed time — decay half-lives, session
 * boundaries, suppression expiry, retention windows, context buckets. With `System.currentTimeMillis()`
 * called at the point of use, none of that can be tested at all: you cannot assert that a preference
 * halves after 90 days without waiting 90 days, and you cannot assert that a bucket survives a
 * timezone change without boarding a plane.
 *
 * This was recorded as testability blocker #2 in `docs/testing/00-state.md`. One interface fixes it,
 * and it costs nothing at runtime.
 */
interface LumaClock {
    fun nowMillis(): Long
    /** Minutes east of UTC, at this instant. Stored per event; see `ListeningEvent.tzOffsetMinutes`. */
    fun timezoneOffsetMinutes(): Int

    companion object {
        /** The real one. */
        val System: LumaClock = object : LumaClock {
            override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
            override fun timezoneOffsetMinutes(): Int =
                TimeZone.getDefault().getOffset( java.lang.System.currentTimeMillis() ) / 60_000
        }
    }
}

/**
 * A clock the tests drive.
 *
 * Deliberately allows time to move *backwards* as well as forwards: device clocks are corrected by
 * NTP, users change them by hand, and daylight saving moves them twice a year. Code that assumes
 * monotonic wall-clock time is wrong, and the only way to find out is to be able to write the test.
 */
class TestClock(
    private var millis: Long = 0L,
    private var offsetMinutes: Int = 0
) : LumaClock {

    override fun nowMillis(): Long = millis
    override fun timezoneOffsetMinutes(): Int = offsetMinutes

    fun advanceBy( deltaMillis: Long ) { millis += deltaMillis }
    fun advanceDays( days: Double ) { millis += ( days * 86_400_000L ).toLong() }
    fun setTo( epochMillis: Long ) { millis = epochMillis }
    fun setTimezoneOffsetMinutes( minutes: Int ) { offsetMinutes = minutes }
}
