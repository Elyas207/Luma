package app.kreate.android.themed.luma

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory

/**
 * Cover art, with a fallback that does not accuse the app of being broken.
 *
 * Coil's default error drawable is a crossed-out picture frame, and artwork goes missing routinely —
 * local files, deleted uploads, a cold start with no network. A large initial says the same thing
 * without the accusation, and still gives the tile a silhouette worth recognising in a list.
 */
@Composable
fun LumaArtwork(
    thumbnailUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = LumaColor.Ember
) {
    var failed by remember( thumbnailUrl ) { mutableStateOf( false ) }

    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    accent.copy( alpha = 0.30f ),
                    LumaColor.Raised
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if ( thumbnailUrl.isNullOrBlank() || failed )
            Text(
                text = title.trim().take( 1 ).uppercase().ifBlank { "·" },
                style = LumaType.Title,
                color = LumaColor.InkSoft,
                textAlign = TextAlign.Center
            )
        else
            Image(
                painter = ImageFactory.rememberAsyncImagePainter(
                    thumbnailUrl = thumbnailUrl,
                    onError = { failed = true }
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
    }
}

/**
 * The disc — Luma's signature object.
 *
 * Circular artwork with playback drawn as the **arc around it** rather than a line underneath. This
 * is the single most load-bearing decision in the redesign: a square cover with a slider below is
 * the shape of literally every music player ever shipped, and no amount of restyling escapes it,
 * whereas a disc with a progress ring is recognisable from across a room and at a glance in a car.
 *
 * The ring is drawn slightly outside the artwork with a gap, so it reads as a halo the cover sits
 * inside rather than as a border stuck to it — the emanation again, at component scale.
 *
 * While playing, the disc breathes: a ~1.5% scale swell on a four-second cycle. Small enough that
 * nobody consciously sees it move, large enough that a paused screen and a playing screen feel
 * different without reading the icon. Playback state becomes something you perceive rather than
 * something you parse.
 */
@Composable
fun LumaDisc(
    thumbnailUrl: String?,
    title: String,
    progress: Float,
    isPlaying: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val breath = rememberInfiniteTransition( label = "disc-breath" )
    val swell by breath.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween( LumaMotion.BREATH_MILLIS, easing = androidx.compose.animation.core.FastOutSlowInEasing ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "swell"
    )

    val scale = if ( isPlaying ) swell else 1f

    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn( 0f, 1f ),
        animationSpec = LumaMotion.fade( 260 ),
        label = "disc-progress"
    )

    Box( modifier.scale( scale ), contentAlignment = Alignment.Center ) {

        // The halo. Drawn first and largest so the artwork lands on top of it.
        Canvas( Modifier.fillMaxSize() ) {
            val stroke = size.minDimension * 0.018f
            val inset = stroke / 2f
            val arcSize = Size( size.width - stroke, size.height - stroke )
            val topLeft = Offset( inset, inset )

            // Track: barely there. Present so the remaining time is legible as an absence.
            drawArc(
                color = LumaColor.Ink.copy( alpha = 0.12f ),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke( width = stroke, cap = StrokeCap.Round )
            )

            if ( sweep > 0f )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            accent,
                            accent.copy( alpha = 0.85f ),
                            accent
                        )
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke( width = stroke, cap = StrokeCap.Round )
                )
        }

        // The cover, inset so the halo has room to be a halo.
        LumaArtwork(
            thumbnailUrl = thumbnailUrl,
            title = title,
            accent = accent,
            modifier = Modifier
                .fillMaxSize()
                .padding( 14.dp )
                .clip( CircleShape )
        )
    }
}

/**
 * A transport control as a ring, not a filled blob.
 *
 * Outlined rather than solid because a screen of filled circles is a remote control, and because an
 * outline lets the atmosphere show through it — the control sits *in* the light rather than on top
 * of it. The primary action gets a filled variant to keep one clear focus per screen.
 *
 * Press feedback is a scale-down on a spring rather than a ripple: a circular ripple inside a
 * circular button is invisible, and the squash reads at arm's length, which is the case that
 * actually matters here.
 */
@Composable
fun LumaRingButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 64.dp,
    filled: Boolean = false,
    accent: Color = LumaColor.Ink,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val squash by animateFloatAsState(
        targetValue = if ( pressed ) 0.90f else 1f,
        animationSpec = LumaMotion.settle(),
        label = "ring-press"
    )

    Box(
        modifier
            .size( diameter )
            .scale( squash )
            .clip( CircleShape )
            .then(
                if ( filled ) Modifier.background( accent )
                else Modifier.border( BorderStroke( 1.5.dp, LumaColor.Ink.copy( alpha = 0.34f ) ), CircleShape )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource( iconRes ),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(
                when {
                    !enabled -> LumaColor.InkFaint
                    filled   -> LumaColor.Ground
                    else     -> LumaColor.Ink
                }
            ),
            modifier = Modifier.size( diameter * 0.36f )
        )
    }
}

/** Wide-tracked caps. Used often enough to be worth not retyping the `uppercase()`. */
@Composable
fun LumaLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LumaColor.InkFaint
) = Text(
    text = text.uppercase(),
    style = LumaType.Label,
    color = color,
    modifier = modifier
)

/**
 * The arch tile.
 *
 * A semicircular top on a squared base — see [LumaShape.Arch] for why the corners are percentages.
 * Used for anything the user picks *from* (mixes, categories, shelves) so that choosing something in
 * Luma always looks like looking through a window, and never like tapping a card.
 */
@Composable
fun LumaArchTile(
    thumbnailUrl: String?,
    title: String,
    subtitle: String?,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if ( pressed ) 0.96f else 1f,
        animationSpec = LumaMotion.settle(),
        label = "arch-press"
    )

    Box(
        modifier
            .scale( squash )
            .clip( LumaShape.Arch )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        LumaArtwork( thumbnailUrl, title, Modifier.fillMaxSize(), accent )

        // Weighted low: the arch's curve is at the top, so darkening the top would fight the shape.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        // Ground, not black — the caption is drawn in Ink, which flips with the
                        // skin, so the scrim has to flip with it too or a light skin ends up with
                        // dark text on a dark base.
                        0.48f to LumaColor.Ground.copy( alpha = 0.38f ),
                        1f to LumaColor.Ground.copy( alpha = 0.92f )
                    )
                )
        )

        androidx.compose.foundation.layout.Column(
            Modifier
                .align( Alignment.BottomStart )
                .padding( horizontal = 16.dp, vertical = 16.dp )
        ) {
            Text(
                text = title,
                style = LumaType.Row,
                color = LumaColor.Ink,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if ( !subtitle.isNullOrBlank() )
                Text(
                    text = subtitle,
                    style = LumaType.Meta,
                    color = LumaColor.InkSoft,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
        }
    }
}

/**
 * The section switcher, shared by both scaffolds.
 *
 * Collapsed it is simply the page's headline; tapping it lists the other sections as an indented
 * index directly beneath, and choosing one collapses it again.
 *
 * This exists as one component because the app previously had *two* answers to the same question:
 * [it.fast4x.rimusic.ui.components.Skeleton] carried a bottom tab bar, and the library carried a
 * horizontal rail of section names. The rail was itself a rewrite of a tab bar — and it did not
 * work, because a horizontal strip of section names **is** a tab strip; setting it in a nicer face
 * changes every attribute of it and no shape. Two components drifting toward the same rejected
 * silhouette is exactly the kind of thing that makes an app look assembled rather than designed.
 *
 * The headline is not a smaller tab strip. It is the page title, and the navigation is folded into
 * it — so the screen gains back a whole bar of height, and the largest text finally names the thing
 * you are looking at instead of naming the app.
 */
@Composable
fun LumaSectionHeadline(
    current: String,
    sections: List<Pair<Int, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: ( Int ) -> Unit,
    modifier: Modifier = Modifier
) = androidx.compose.foundation.layout.Column(
    modifier
        .fillMaxWidth()
        .padding( start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp )
        // Opening pushes content down rather than covering it: nothing is hidden behind an overlay
        // the user has to dismiss before they can read the page again.
        .animateContentSize( animationSpec = LumaMotion.settle() )
) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .clip( CircleShape )
            .clickable( enabled = sections.size > 1, onClick = onToggle )
            .padding( vertical = 2.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = current,
            style = LumaType.Hero,
            color = LumaColor.Ink,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        if ( sections.size > 1 ) {
            androidx.compose.foundation.layout.Spacer( Modifier.size( 10.dp ) )

            val turn by animateFloatAsState(
                targetValue = if ( expanded ) 180f else 0f,
                animationSpec = LumaMotion.settle(),
                label = "headline-chevron"
            )

            Image(
                painter = painterResource( R.drawable.chevron_down ),
                contentDescription = if ( expanded ) "Hide sections" else "Show sections",
                colorFilter = ColorFilter.tint( LumaColor.InkFaint ),
                modifier = Modifier.size( 22.dp ).rotate( turn )
            )
        }
    }

    if ( expanded )
        androidx.compose.foundation.layout.Column( Modifier.padding( top = 6.dp ) ) {
            sections.filter { it.second != current }
                    .forEach { ( index, label ) ->
                        Text(
                            text = label,
                            style = LumaType.Section,
                            color = LumaColor.InkSoft,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip( CircleShape )
                                .clickable { onSelect( index ) }
                                .padding( vertical = 7.dp )
                        )
                    }
        }
}
