package app.kreate.android.themed.luma

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Bitmap
import androidx.palette.graphics.Palette
import app.kreate.android.coil3.ImageFactory
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * The light the interface is lit by.
 *
 * Every other dark media app treats artwork as *content* — a square placed on a neutral background.
 * Luma treats it as the **light source**: one colour is pulled out of the cover and bled across the
 * whole screen as a soft radial emanation, so the room changes with what is playing. This is the
 * literal drawing of the mark, and it is the cheapest possible way to make an app feel alive without
 * a single particle effect.
 *
 * Two decisions worth writing down, because both were arrived at by rejecting the obvious version:
 *
 * - **Not a blurred cover.** Blurring a bright sleeve yields a bright screen, which is how the old
 *   player ended up with near-white text on near-white blur (already fixed once, with a scrim). A
 *   single extracted hue can be held at a chosen luminance, so contrast is a property of the design
 *   rather than a property of whatever the artist's cover happened to be.
 * - **Not a flat wash.** A flat artwork-coloured field (Deezer's approach) is striking but static,
 *   and at full-screen scale a solid saturated colour is tiring. A radial that falls off to the
 *   ground colour keeps the drama at the focus and leaves the edges calm.
 */

/**
 * Pull one colour out of the artwork.
 *
 * Preference order is vibrant → muted → dominant, because a vibrant swatch is the one a person would
 * name if asked "what colour is this cover". The result is animated rather than snapped so a track
 * change reads as the room's light shifting, not as a repaint.
 */
@Composable
fun rememberArtworkAccent( thumbnailUrl: String? ): State<Color> {

    var target by remember { mutableStateOf( LumaColor.Ember ) }

    LaunchedEffect( thumbnailUrl ) {
        if ( thumbnailUrl.isNullOrBlank() ) {
            target = LumaColor.Ember
            return@LaunchedEffect
        }

        // A decorative background colour is never worth a crash. `Palette` reads raw pixels and
        // decoders fail in ways that are not worth enumerating; if anything goes wrong the screen
        // simply keeps the previous light.
        val extracted = withContext( Dispatchers.Default ) {
          runCatching {
            ImageFactory.bitmap( thumbnailUrl ) {
                            // Coil hands back a `Config.HARDWARE` bitmap by default, whose pixels
                            // live on the GPU and cannot be read back — `Palette` calls
                            // `getPixels()` and throws `IllegalStateException`, taking the process
                            // with it. Asking for a software bitmap up front is cheaper than
                            // copying one out afterwards.
                            allowHardware( false )
                        }
                        .getOrNull()
                        // Belt and braces: any future caller of `bitmap()` that reinstates hardware
                        // config would otherwise crash the app rather than lose a background tint.
                        ?.takeIf { it.config != Bitmap.Config.HARDWARE }
                        ?.let { bitmap ->
                            val palette = Palette.from( bitmap )
                                                 // One colour is wanted, not a faithful histogram.
                                                 // Capping the sampled area keeps a 1200px cover
                                                 // from costing tens of milliseconds for an answer
                                                 // that is identical either way.
                                                 .resizeBitmapArea( 128 * 128 )
                                                 .clearFilters()
                                                 .generate()

                            palette.vibrantSwatch
                                ?: palette.lightVibrantSwatch
                                ?: palette.mutedSwatch
                                ?: palette.dominantSwatch
                        }
                        ?.let { Color( it.rgb ) }
          }.getOrNull()
        }

        target = extracted?.let( ::liftForGlow ) ?: LumaColor.Ember
    }

    return animateColorAsState(
        targetValue = target,
        animationSpec = LumaMotion.fade( 900 ),
        label = "artwork-accent"
    )
}

/**
 * Force an extracted colour into a usable band.
 *
 * Covers routinely yield something almost black or almost white — a dark album sleeve gives a
 * swatch that emits no light at all, and a white one gives a glow that greys the whole screen.
 * Clamping saturation and lightness means the emanation is always *visible* and never *washes*,
 * whatever the artwork does.
 */
private fun liftForGlow( color: Color ): Color {
    val hsl = FloatArray( 3 )
    androidx.core.graphics.ColorUtils.colorToHSL( color.toArgb(), hsl )

    /*
     * The upper saturation bound was 0.85, which is very nearly neon.
     *
     * A vibrant swatch pulled from a cover is often already at the top of that range, so a sunset
     * sleeve produced a pure red glow *and* a pure red play button — an icon on a fill of almost the
     * same hue and lightness, which is both hard to read and the one thing on screen shouting
     * loudest. Holding saturation in the middle keeps the room recognisably the colour of the record
     * while leaving the accent something a dark glyph can sit on.
     */
    hsl[1] = hsl[1].coerceIn( 0.28f, 0.58f )
    hsl[2] = hsl[2].coerceIn( 0.52f, 0.68f )

    return Color( androidx.core.graphics.ColorUtils.HSLToColor( hsl ) )
}

/**
 * The emanation itself.
 *
 * Two offset radials rather than one: a single centred glow reads as a vignette, which is a
 * photographic effect and looks like a filter. Two sources at different sizes and opacities read as
 * depth. They drift on a very slow cycle — a full orbit takes about a minute, which is below the
 * rate at which motion becomes something you notice and start to resent, but above the rate at which
 * a still screen starts to feel dead.
 *
 * @param accent the colour from [rememberArtworkAccent]
 * @param intensity 0f–1f. Car Mode runs this low; the player runs it high.
 */
@Composable
fun LumaAtmosphere(
    accent: Color,
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    animated: Boolean = true
) {
    val drift = rememberInfiniteTransition( label = "atmosphere-drift" )

    val phase by if ( animated )
        drift.animateFloat(
            initialValue = 0f,
            targetValue = ( 2 * Math.PI ).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween( 58_000, easing = LinearEasing ),
                repeatMode = RepeatMode.Restart
            ),
            label = "drift-phase"
        )
    else
        remember { mutableStateOf( 0f ) }

    Box( modifier.background( LumaColor.Ground ) ) {
        Canvas( Modifier.fillMaxSize() ) {

            val w = size.width
            val h = size.height

            // Primary source: upper area, large, the one that sets the mood.
            val primary = Offset(
                x = w * ( 0.30f + 0.10f * cos( phase ) ),
                y = h * ( 0.24f + 0.06f * sin( phase ) )
            )

            // Secondary: lower and opposite, so the falloff never looks symmetrical.
            val secondary = Offset(
                x = w * ( 0.82f - 0.12f * cos( phase * 0.7f ) ),
                y = h * ( 0.74f + 0.05f * sin( phase * 0.7f ) )
            )

            val reach = maxOf( w, h )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy( alpha = 0.34f * intensity ),
                        accent.copy( alpha = 0.12f * intensity ),
                        Color.Transparent
                    ),
                    center = primary,
                    radius = reach * 0.92f
                ),
                size = size
            )

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy( alpha = 0.20f * intensity ),
                        Color.Transparent
                    ),
                    center = secondary,
                    radius = reach * 0.58f
                ),
                size = size
            )

            // Settle the whole thing back toward the ground colour at the bottom, so text laid over
            // the lower third has a guaranteed floor to sit on regardless of where the glow drifted.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.55f to LumaColor.Ground.copy( alpha = 0.35f ),
                    1f to LumaColor.Ground.copy( alpha = 0.88f )
                ),
                size = size
            )
        }
    }
}
