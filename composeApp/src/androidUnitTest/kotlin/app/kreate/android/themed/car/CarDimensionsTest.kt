package app.kreate.android.themed.car

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Car Mode's safety rules, asserted rather than assumed.
 *
 * These are not style preferences — they are the constraints that make the mode usable from the
 * driver's seat, and they are exactly the kind of thing that erodes silently when someone later
 * nudges a value to make a layout fit. Encoding them here means shrinking a control below the
 * safe threshold fails the build instead of shipping.
 */
class CarDimensionsTest {

    /** Android's baseline minimum. Car Mode deliberately exceeds it everywhere. */
    private val androidBaselineDp = 48

    @Test
    fun `every touch target clears the car minimum`() {
        val targets = mapOf(
            "TOUCH_TARGET" to CarDimensions.TOUCH_TARGET,
            "TRANSPORT_SECONDARY" to CarDimensions.TRANSPORT_SECONDARY,
            "TRANSPORT_PRIMARY" to CarDimensions.TRANSPORT_PRIMARY,
            "QUEUE_ROW_HEIGHT" to CarDimensions.QUEUE_ROW_HEIGHT
        )

        targets.forEach { ( name, size ) ->
            assertTrue(
                "$name is ${size.value}dp, below the 64dp car minimum",
                size.value >= 64f
            )
        }
    }

    @Test
    fun `touch targets are meaningfully larger than the phone baseline`() {
        // An unsupported hand aiming at a moving target needs more than the phone minimum.
        assertTrue( CarDimensions.TOUCH_TARGET.value > androidBaselineDp )
    }

    @Test
    fun `play pause is the largest control on screen`() {
        // The one control that must be findable without looking.
        assertTrue(
            "play/pause must be larger than skip",
            CarDimensions.TRANSPORT_PRIMARY.value > CarDimensions.TRANSPORT_SECONDARY.value
        )
        assertTrue(
            "skip must be larger than the generic target",
            CarDimensions.TRANSPORT_SECONDARY.value > CarDimensions.TOUCH_TARGET.value
        )
    }

    @Test
    fun `no text size falls below the glanceable floor`() {
        val sizes = mapOf(
            "MIN_TEXT" to CarDimensions.MIN_TEXT,
            "LABEL_TEXT" to CarDimensions.LABEL_TEXT,
            "QUEUE_TITLE_TEXT" to CarDimensions.QUEUE_TITLE_TEXT,
            "SUBTITLE_TEXT" to CarDimensions.SUBTITLE_TEXT,
            "TITLE_TEXT" to CarDimensions.TITLE_TEXT
        )

        sizes.forEach { ( name, size ) ->
            assertTrue(
                "$name is ${size.value}sp, below the 18sp readable-at-a-glance floor",
                size.value >= 18f
            )
        }
    }

    @Test
    fun `type scale is ordered so hierarchy survives a glance`() {
        assertTrue( CarDimensions.TITLE_TEXT.value > CarDimensions.SUBTITLE_TEXT.value )
        assertTrue( CarDimensions.SUBTITLE_TEXT.value > CarDimensions.QUEUE_TITLE_TEXT.value )
        assertTrue( CarDimensions.QUEUE_TITLE_TEXT.value >= CarDimensions.LABEL_TEXT.value )
    }

    @Test
    fun `controls are spaced far enough apart to avoid mis-taps`() {
        // Adjacent controls are the mis-tap risk that matters: reaching for pause and hitting skip.
        assertTrue( CarDimensions.CONTROL_GAP.value >= 16f )
    }

    @Test
    fun `scrubber is thick enough to grab without precision`() {
        assertTrue( CarDimensions.SCRUBBER_HEIGHT.value >= 12f )
    }
}
