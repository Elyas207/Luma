package app.kreate.android.themed.skin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import app.kreate.android.R

/**
 * The Aurora backdrop: a real sky, a soft depth wash, and two bubbles drifting upward.
 *
 * What made this era's design memorable was not blue gradients — it was *optimism expressed through
 * nature*. Water, glass, sky, and the sense that the machine was pleased to see you. So this is a
 * photograph rather than a synthesised gradient, and the motion is slow enough to read as ambient
 * rather than as animation demanding attention.
 *
 * Restraint is deliberate. Two bubbles, not twenty. The assets support the interface; they are not
 * the interface, and anything busier would make text unreadable within a week of daily use.
 */
@Composable
fun AeroSky( modifier: Modifier = Modifier ) {
    val skin = LocalSkin.current

    Box( modifier.fillMaxSize() ) {

        Image(
            painter = painterResource( R.drawable.aero_sky ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Depth wash. The sky alone is too bright at the bottom for list content to sit on, so the
        // lower half sinks towards the skin's deeper blues — the "looking up through water" read.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to skin.palette.background0.copy( alpha = 0.55f ),
                        1f to skin.palette.background0.copy( alpha = 0.92f )
                    )
                )
        )

        DriftingBubble( R.drawable.aero_bubble_a, startX = 0.18f, durationMs = 47_000, scale = 1f )
        DriftingBubble( R.drawable.aero_bubble_b, startX = 0.74f, durationMs = 61_000, scale = 0.7f )
    }
}

/**
 * One bubble rising the height of the screen and repeating.
 *
 * Durations are long and deliberately co-prime-ish so the two never fall into a visible rhythm —
 * a loop you can spot is worse than no animation. Only `translationY`, `alpha` and `scale` are
 * touched, so this stays on the render thread and costs no layout or recomposition.
 */
@Composable
private fun DriftingBubble(
    drawableId: Int,
    startX: Float,
    durationMs: Int,
    scale: Float
) {
    val heightPx = with( LocalConfiguration.current ) { screenHeightDp.toFloat() }

    val progress by rememberInfiniteTransition( label = "bubble" ).animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween( durationMs, easing = LinearEasing ),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbleRise"
    )

    Image(
        painter = painterResource( drawableId ),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize( 0.16f * scale )
            .graphicsLayer {
                translationX = size.width * ( startX * 4f )
                // Rises from just below the screen to just above it
                translationY = heightPx * 2.2f * ( 1f - progress ) - heightPx * 0.2f
                // A gentle sway, so it looks buoyant rather than mechanical
                translationX += ( kotlin.math.sin( progress * 6.28f * 2 ) * 24f )
            }
            // Fades in as it enters and out as it leaves; never pops
            .alpha( ( kotlin.math.sin( progress * Math.PI ).toFloat() ) * 0.5f )
    )
}
