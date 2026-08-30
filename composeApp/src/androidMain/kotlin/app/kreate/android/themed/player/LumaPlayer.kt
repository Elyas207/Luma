package app.kreate.android.themed.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.luma.LumaAtmosphere
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaDisc
import app.kreate.android.themed.luma.LumaLabel
import app.kreate.android.themed.luma.LumaMotion
import app.kreate.android.themed.luma.LumaRingButton
import app.kreate.android.themed.luma.LumaType
import app.kreate.android.themed.luma.rememberArtworkAccent
import app.kreate.util.cleanPrefix
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.utils.positionAndDurationState
import org.koin.compose.koinInject
import kotlin.math.abs

/**
 * The player — Luma's centre of gravity.
 *
 * The screen this replaces carried thirteen controls, with `repeat` appearing **twice** and three
 * timestamps on screen at once. That was cut to nine in an earlier pass, which fixed the clutter but
 * left the *shape* untouched: a rounded-square cover, a title under it, a line scrubber, a row of
 * glyphs. That silhouette is Spotify's, Apple Music's, Deezer's and YouTube Music's, and no amount
 * of retyping or recolouring escapes it — put four screenshots side by side and you cannot tell
 * whose is whose.
 *
 * So this version changes the shapes, which is the only thing a squint test actually registers:
 *
 * - **The cover is a disc, and progress is the ring around it.** One object instead of two, and the
 *   only music player silhouette in the category that isn't a rectangle. It also happens to be the
 *   most glanceable — an arc communicates "how far through" peripherally, which a 4dp line does not.
 * - **The transport is three rings**, outlined so the atmosphere shows through them, with only the
 *   play control filled. One focus, not a row of equal blobs.
 * - **The title is a display serif**, large and centred on the disc's axis. Centring is normally the
 *   wrong call — a centred title shifts horizontally as tracks change and the eye has to re-find it
 *   — but that objection dissolves when there is a fixed circular axis for it to hang from, and the
 *   composition is radial rather than a left-aligned stack.
 * - **The screen is lit by the record.** One colour is pulled from the artwork and bled out behind
 *   everything as a slow-drifting emanation. The app looks different every day without a single
 *   random gradient.
 *
 * Still nine controls, still no duplicates, still one clock.
 */
@Composable
fun LumaPlayer(
    navController: NavController,
    onDismiss: () -> Unit,
    onOpenMenu: ( MediaItem ) -> Unit,
    modifier: Modifier = Modifier
) {
    val player: StatefulPlayer = koinInject()
    val mediaItem by player.currentMediaItemState.collectAsState()

    val item = mediaItem ?: return

    val positionAndDuration by player.positionAndDurationState()
    val ( position, duration ) = positionAndDuration

    val isPlaying = rememberIsPlaying( player )
    val isLiked by remember( item.mediaId ) {
        Database.songTable.isLiked( item.mediaId )
    }.collectAsState( false )

    val artworkUrl = item.mediaMetadata.artworkUri?.toString()
    val accent by rememberArtworkAccent( artworkUrl )

    val title = cleanPrefix( item.mediaMetadata.title?.toString().orEmpty() )
    val artist = cleanPrefix( item.mediaMetadata.artist?.toString().orEmpty() )

    val progress = if ( duration > 0 ) position.toFloat() / duration else 0f

    val isWide = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    Box(
        modifier
            .fillMaxSize()
            // The atmosphere is the background; it must not respond to the swipe gesture below,
            // so it is drawn as a sibling rather than wrapping the content.
            .background( LumaColor.Ground )
    ) {
        LumaAtmosphere( accent, Modifier.fillMaxSize() )

        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                // Horizontal swipe changes track. Kept from the previous version because it is the
                // one gesture people genuinely expect on a player and it costs no screen space.
                .pointerInput( Unit ) {
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if ( abs( travelled ) > 90f )
                                if ( travelled < 0 ) player.seekToNext() else player.seekToPrevious()
                            travelled = 0f
                        }
                    ) { _, delta -> travelled += delta }
                }
        ) {
            TopRow( onDismiss ) { onOpenMenu( item ) }

            if ( isWide )
                WidePlayer(
                    player, title, artist, artworkUrl, accent,
                    progress, position, duration, isPlaying, isLiked, navController
                )
            else
                TallPlayer(
                    player, title, artist, artworkUrl, accent,
                    progress, position, duration, isPlaying, isLiked, navController
                )
        }
    }
}

/**
 * Portrait: one vertical axis, disc at the optical centre.
 *
 * The disc takes ~78% of the width rather than filling it. A cover that touches both edges reads as
 * a wallpaper and loses its object-ness; leaving air around it is what makes it feel like a thing
 * sitting in a lit room, which is the entire premise.
 */
@Composable
private fun TallPlayer(
    player: StatefulPlayer,
    title: String,
    artist: String,
    artworkUrl: String?,
    accent: Color,
    progress: Float,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLiked: Boolean,
    navController: NavController
) = Column(
    Modifier
        .fillMaxSize()
        .padding( horizontal = 28.dp ),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Spacer( Modifier.height( 56.dp ) )

    LumaLabel( "Now playing", color = LumaColor.InkFaint )

    Spacer( Modifier.weight( 0.6f ) )

    LumaDisc(
        thumbnailUrl = artworkUrl,
        title = title,
        progress = progress,
        isPlaying = isPlaying,
        accent = accent,
        modifier = Modifier
            .fillMaxWidth( 0.86f )
            .aspectRatio( 1f )
    )

    Spacer( Modifier.weight( 0.5f ) )

    TrackHeading( title, artist, Alignment.CenterHorizontally, TextAlign.Center )

    Spacer( Modifier.height( 26.dp ) )

    Scrubber( position, duration, accent, player::seekTo )

    Spacer( Modifier.height( 30.dp ) )

    Transport( player, isPlaying, accent )

    Spacer( Modifier.weight( 0.5f ) )

    SecondaryRow( player, isLiked, navController )

    Spacer( Modifier.height( 18.dp ) )
}

/**
 * Landscape and tablet: disc left, everything read on the right.
 *
 * Not the portrait layout stretched. A landscape screen is wide and *short*, so a centred vertical
 * stack would shrink the disc to fit the height and then leave two enormous empty margins — the
 * exact "empty space with no purpose" failure. Splitting the axis lets the disc stay as large as the
 * height allows while the text gets a proper measure beside it.
 */
@Composable
private fun WidePlayer(
    player: StatefulPlayer,
    title: String,
    artist: String,
    artworkUrl: String?,
    accent: Color,
    progress: Float,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    isLiked: Boolean,
    navController: NavController
) = Row(
    Modifier
        .fillMaxSize()
        .padding( start = 40.dp, end = 40.dp, top = 64.dp, bottom = 28.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Box(
        Modifier
            .weight( 0.44f )
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        LumaDisc(
            thumbnailUrl = artworkUrl,
            title = title,
            progress = progress,
            isPlaying = isPlaying,
            accent = accent,
            modifier = Modifier
                .fillMaxHeight( 0.92f )
                .aspectRatio( 1f )
        )
    }

    Spacer( Modifier.width( 44.dp ) )

    Column(
        Modifier
            .weight( 0.56f )
            // A heading set across a 1200dp column is genuinely hard to track line to line.
            .widthIn( max = 620.dp ),
        verticalArrangement = Arrangement.Center
    ) {
        LumaLabel( "Now playing", color = LumaColor.InkFaint )

        Spacer( Modifier.height( 18.dp ) )

        TrackHeading( title, artist, Alignment.Start, TextAlign.Start )

        Spacer( Modifier.height( 34.dp ) )

        Scrubber( position, duration, accent, player::seekTo )

        Spacer( Modifier.height( 34.dp ) )

        Transport( player, isPlaying, accent, centred = false )

        Spacer( Modifier.height( 30.dp ) )

        SecondaryRow( player, isLiked, navController, centred = false )
    }
}

/** Dismiss and overflow. Nothing else — you know which app you are in, you are looking at its player. */
@Composable
private fun TopRow( onDismiss: () -> Unit, onOpenMenu: () -> Unit ) = Row(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 18.dp, vertical = 10.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Glyph( R.drawable.chevron_down, "Close", onClick = onDismiss )
    Spacer( Modifier.weight( 1f ) )
    Glyph( R.drawable.ellipsis_vertical, "More", onClick = onOpenMenu )
}

/**
 * Title and artist.
 *
 * The serif does the work here. At 38sp with negative tracking it is unmistakably not a system
 * list, and the wide-tracked caps above it are the counterweight that keeps it reading as editorial
 * rather than merely decorative.
 */
@Composable
private fun TrackHeading(
    title: String,
    artist: String,
    align: Alignment.Horizontal,
    textAlign: TextAlign
) = Column(
    Modifier.fillMaxWidth(),
    horizontalAlignment = align
) {
    Text(
        text = title,
        style = LumaType.Title,
        color = LumaColor.Ink,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    Spacer( Modifier.height( 8.dp ) )

    Text(
        text = artist,
        style = LumaType.Meta,
        color = LumaColor.InkSoft,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * One scrubber, two times: elapsed and **remaining**.
 *
 * Remaining rather than total, because "how long until this ends" is the question people actually
 * have — total duration is a fact about the file, not about the moment. The times flank the line
 * rather than sitting under its ends, which keeps the whole control on one row and stops the
 * timestamps reading as two separate labels.
 *
 * The track is 2dp. A scrubber thick enough to look like a component competes with the progress ring
 * for the same job; here it is a fine measure, and the ring is the one you read at a glance.
 */
@Composable
private fun Scrubber(
    position: Long,
    duration: Long,
    accent: Color,
    onSeek: ( Long ) -> Unit
) {
    var dragFraction by remember { mutableFloatStateOf( -1f ) }
    var width by remember { mutableFloatStateOf( 1f ) }

    val live = if ( duration > 0 ) position.toFloat() / duration else 0f
    val shown = if ( dragFraction >= 0f ) dragFraction else live

    val animated by animateFloatAsState(
        targetValue = shown,
        animationSpec = if ( dragFraction >= 0f ) LumaMotion.fade( 0 ) else LumaMotion.fade( 240 ),
        label = "scrub"
    )

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = ( shown * duration ).toLong().coerceAtLeast( 0L ).asClock(),
            style = LumaType.Label,
            color = LumaColor.InkFaint
        )

        Box(
            Modifier
                .weight( 1f )
                .padding( horizontal = 14.dp )
                // A 2dp line is far too small a touch target, so the row keeps a 32dp tall hit area
                // around it. The visual weight and the touch weight are allowed to differ.
                .height( 32.dp )
                .pointerInput( duration ) {
                    width = size.width.toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragFraction = ( offset.x / size.width ).coerceIn( 0f, 1f )
                        },
                        onDragEnd = {
                            if ( dragFraction >= 0f && duration > 0 )
                                onSeek( ( dragFraction * duration ).toLong() )
                            dragFraction = -1f
                        },
                        onDragCancel = { dragFraction = -1f }
                    ) { change, _ ->
                        dragFraction = ( change.position.x / size.width ).coerceIn( 0f, 1f )
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height( 2.dp )
                    .clip( CircleShape )
                    .background( LumaColor.Ink.copy( alpha = 0.16f ) )
            )
            Box(
                Modifier
                    .fillMaxWidth( animated.coerceIn( 0f, 1f ) )
                    .height( 2.dp )
                    .clip( CircleShape )
                    .background( accent )
            )
        }

        Text(
            text = "-" + ( duration - ( shown * duration ).toLong() ).coerceAtLeast( 0L ).asClock(),
            style = LumaType.Label,
            color = LumaColor.InkFaint
        )
    }
}

/**
 * Previous, play, next — as rings.
 *
 * Only the play control is filled, so there is exactly one focus. The skip controls are outlined and
 * smaller, which is also the correct weighting by frequency: play/pause is pressed far more than
 * either neighbour.
 */
@Composable
private fun Transport(
    player: StatefulPlayer,
    isPlaying: Boolean,
    accent: Color,
    centred: Boolean = true
) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = if ( centred ) Arrangement.Center else Arrangement.Start,
    verticalAlignment = Alignment.CenterVertically
) {
    LumaRingButton(
        iconRes = R.drawable.play_skip_back,
        contentDescription = "Previous",
        onClick = player::seekToPrevious,
        diameter = 60.dp
    )

    Spacer( Modifier.width( 22.dp ) )

    LumaRingButton(
        iconRes = if ( isPlaying ) R.drawable.pause else R.drawable.play,
        contentDescription = if ( isPlaying ) "Pause" else "Play",
        onClick = { if ( isPlaying ) player.pause() else player.play() },
        diameter = 84.dp,
        filled = true,
        accent = accent
    )

    Spacer( Modifier.width( 22.dp ) )

    LumaRingButton(
        iconRes = R.drawable.play_skip_forward,
        contentDescription = "Next",
        onClick = player::seekToNext,
        diameter = 60.dp
    )
}

/**
 * The three that are not transport: favourite, shuffle, queue.
 *
 * Rendered as wide-tracked words rather than another row of glyphs. Fourteen unlabelled icons is how
 * the library screen became unreadable, and the same logic applies at small scale — an icon row
 * makes every item look equally important and none of them legible. Words are unambiguous, and at
 * this size they are visually quieter than the glyphs they replace.
 */
@Composable
private fun SecondaryRow(
    player: StatefulPlayer,
    isLiked: Boolean,
    navController: NavController,
    centred: Boolean = true
) {
    val modes = rememberPlaybackModes( player )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if ( centred ) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WordAction(
            label = if ( isLiked ) "Loved" else "Love",
            active = isLiked,
            onClick = {
                Database.asyncTransaction { songTable.toggleLike( player.currentMediaItem?.mediaId.orEmpty() ) }
            }
        )

        Spacer( Modifier.width( 30.dp ) )

        WordAction(
            label = "Shuffle",
            active = modes.shuffle,
            onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }
        )

        Spacer( Modifier.width( 30.dp ) )

        WordAction(
            label = "Queue",
            active = false,
            onClick = { NavRoutes.queue.navigateHere( navController ) }
        )
    }
}

/** A secondary action as a word, with state carried by weight rather than by a filled icon. */
@Composable
private fun WordAction(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) = Text(
    text = label.uppercase(),
    style = LumaType.Label,
    color = if ( active ) LumaColor.Ink else LumaColor.InkFaint,
    modifier = Modifier
        .clip( CircleShape )
        .clickable( onClick = onClick )
        .padding( horizontal = 10.dp, vertical = 12.dp )
)

/** A bare icon, for the two chrome controls that are not part of playback. */
@Composable
private fun Glyph(
    iconRes: Int,
    contentDescription: String,
    size: Dp = 22.dp,
    onClick: () -> Unit
) = Box(
    Modifier
        .size( 44.dp )
        .clip( CircleShape )
        .clickable( onClick = onClick ),
    contentAlignment = Alignment.Center
) {
    Image(
        painter = painterResource( iconRes ),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint( LumaColor.InkSoft ),
        modifier = Modifier.size( size )
    )
}

@Composable
private fun rememberIsPlaying( player: Player ): Boolean {

    var isPlaying by remember { mutableStateOf( player.isPlaying ) }

    DisposableEffect( player ) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged( playing: Boolean ) { isPlaying = playing }
        }
        player.addListener( listener )
        onDispose { player.removeListener( listener ) }
    }

    return isPlaying
}

private data class PlaybackModes( val shuffle: Boolean, val repeat: Int )

@Composable
private fun rememberPlaybackModes( player: Player ): PlaybackModes {

    var modes by remember {
        mutableStateOf( PlaybackModes( player.shuffleModeEnabled, player.repeatMode ) )
    }

    DisposableEffect( player ) {
        val listener = object : Player.Listener {
            override fun onShuffleModeEnabledChanged( enabled: Boolean ) {
                modes = modes.copy( shuffle = enabled )
            }
            override fun onRepeatModeChanged( mode: Int ) {
                modes = modes.copy( repeat = mode )
            }
        }
        player.addListener( listener )
        onDispose { player.removeListener( listener ) }
    }

    return modes
}

/** `m:ss`, or `h:mm:ss` once an hour is on the clock — long recitations are routine here. */
private fun Long.asClock(): String {
    val total = this / 1000
    val seconds = total % 60
    val minutes = ( total / 60 ) % 60
    val hours = total / 3600

    return if ( hours > 0 )
        "%d:%02d:%02d".format( hours, minutes, seconds )
    else
        "%d:%02d".format( minutes, seconds )
}
