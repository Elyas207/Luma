package app.kreate.android.screens.player.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.kreate.android.coil3.BlurTransformation
import app.kreate.android.coil3.ImageFactory
import it.fast4x.rimusic.utils.isAtLeastAndroid12
import me.knighthat.component.player.BlurAdjuster
import kotlin.math.sqrt

@Composable
private fun BlurFilter(
    thumbnailUrl: String,
    showThumbnail: Boolean,
    isShowingLyrics: Boolean,
    isShowingVisualizer: Boolean,
    noBlur: Boolean,
    blurAdjuster: BlurAdjuster,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) = BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
) {
    /**
     * Adapt to any changes of screen's resolution.
     * [derivedStateOf] don't work because both values
     * aren't observable - issue #266 (Android 26-)
     */
    val size = remember( maxWidth, maxHeight ) {
        maxOf( this.maxWidth, maxHeight )
    }
    // Probably not a best fit, but it gets the job done
    val scale = with(LocalDensity.current) {
        val w = maxWidth.toPx()
        val h = maxHeight.toPx()
        val s = size.toPx()

        sqrt(w * w + h * h) / s
    }
    Box( Modifier.requiredSize( size ) ) {
        val blurRadius by remember {
            derivedStateOf(structuralEqualityPolicy()) {
                if (showThumbnail || (isShowingLyrics && !isShowingVisualizer) || !noBlur)
                    blurAdjuster.strength
                else
                    0f
            }
        }
        val blurTransformation by remember {
            derivedStateOf(referentialEqualityPolicy()) {
                if (!isAtLeastAndroid12) {
                    val radius: Float =
                        if (showThumbnail || (isShowingLyrics && !isShowingVisualizer) || !noBlur)
                            blurAdjuster.strength
                        else
                            0f
                    listOf(BlurTransformation(radius.toInt(), .5f))
                } else
                    emptyList()
            }
        }
        val angle by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = if (blurAdjuster.isCoverRotating) 360f else 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 30000, easing = LinearEasing)
            )
        )

        ImageFactory.AsyncImage(
            thumbnailUrl = thumbnailUrl,
            transformations = blurTransformation,
            contentDescription = "blurred_background",
            contentScale = ContentScale.Fit,
            // [Modifier.blur] will be ignore on unsupported devices by default
            modifier = modifier.fillMaxSize()
                               .blur( blurRadius.dp )       // ignored on unsupported devices by default
                               .graphicsLayer {
                                   scaleX = scale
                                   scaleY = scale
                                   rotationZ = angle
                               }
        )

        /*
         * Scrim over the cover.
         *
         * The player draws its text with a palette derived from the artwork on the assumption of a
         * dark backdrop — but the backdrop *is* the artwork, and a bright cover leaves near-white
         * text on a near-white blur. On a yellow sleeve the track title was effectively invisible.
         *
         * Blur alone does not fix this: blurring a bright image yields a bright image. Only
         * lowering the backdrop's luminance guarantees the contrast, so this darkens whatever is
         * behind the text rather than trying to out-guess the palette per cover.
         */
        Box(
            Modifier.matchParentSize()
                    .background( Color.Black.copy( alpha = SCRIM_ALPHA ) )
        )
    }
}

/**
 * Enough to hold text legible over a white cover, light enough that the artwork still reads as the
 * background rather than as a grey panel.
 */
private const val SCRIM_ALPHA = 0.45f

@Composable
private fun Backdrop( blurAdjuster: BlurAdjuster, modifier: Modifier = Modifier ) {
    // Backdrop is supported regardless of Android version
    val backdropColor by remember {
        derivedStateOf {
            Color.Black.copy(
                // Ensure value can't go outside by accident
                alpha = (blurAdjuster.backdrop / 100f).coerceIn( 0f, 1f )
            )
        }
    }
    Box( modifier.fillMaxSize().background( backdropColor ) )
}

@Composable
fun BlurredCover(
    thumbnailUrl: String,
    blurAdjuster: BlurAdjuster,
    showThumbnail: Boolean,
    noBlur: Boolean,
    isShowingLyrics: Boolean,
    isShowingVisualizer: Boolean,
    contentScale: ContentScale,
    modifier: Modifier = Modifier
) {
    BlurFilter( thumbnailUrl, showThumbnail, isShowingLyrics, isShowingVisualizer, noBlur, blurAdjuster, contentScale, modifier )
    Backdrop( blurAdjuster, modifier )
}