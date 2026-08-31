package app.kreate.android.themed.car

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The measurements Car Mode is built from.
 *
 * These are not taste; they are the constraints of using a screen bolted to a dashboard. A phone
 * UI assumes a still device, both eyes, and a steady hand. A centre display gets a glance, one
 * hand, at arm's length, while the vehicle moves. Every number here follows from that:
 *
 * - Targets are far larger than the 48 dp Android baseline, because the hand is unsupported and
 *   the target is moving relative to it.
 * - Type has a floor. Anything under [MIN_TEXT] cannot be read in a glance, so it has no business
 *   on this screen — if information needs smaller text, it does not belong in Car Mode.
 * - Spacing is generous, because adjacent controls that are easy to confuse are the failure mode
 *   that matters: hitting "skip" when reaching for "pause" is worse than either button being
 *   slightly smaller.
 */
object CarDimensions {

    /** Floor for anything tappable. Well above the 48 dp baseline — see class docs. */
    val TOUCH_TARGET = 64.dp

    /** Secondary transport (previous / next). */
    val TRANSPORT_SECONDARY = 88.dp

    /** Play/pause. The one control that must be findable without looking, so it is the largest. */
    val TRANSPORT_PRIMARY = 112.dp

    /** Queue rows — full width, so height alone decides how hard they are to hit. */
    val QUEUE_ROW_HEIGHT = 80.dp

    /** Gap between adjacent controls. Deliberately wide: mis-taps matter more than density. */
    val CONTROL_GAP = 24.dp

    /** Page margin. */
    val EDGE = 32.dp

    /** Scrubber height — thick enough to grab without precision. */
    val SCRUBBER_HEIGHT = 16.dp

    /** Absolute minimum type size anywhere in Car Mode. */
    val MIN_TEXT = 18.sp

    /** Now-playing track title. */
    val TITLE_TEXT = 40.sp

    /** Artist, and other supporting metadata. */
    val SUBTITLE_TEXT = 24.sp

    /** Section headers ("UP NEXT"). */
    val LABEL_TEXT = 18.sp

    /** Queue row titles. */
    val QUEUE_TITLE_TEXT = 22.sp
}
