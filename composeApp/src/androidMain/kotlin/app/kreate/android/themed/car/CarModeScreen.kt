package app.kreate.android.themed.car

import app.kreate.android.themed.luma.LumaRadius

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.forcePlay
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.themed.skin.SkinOrnamentLayer
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.isVideo
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.ui.screens.player.components.VideoSurface
import org.koin.compose.koinInject

/**
 * Car Mode — a separate surface, not the tablet layout stretched wide.
 *
 * Three zones, none nested more than one level deep, all visible at once:
 *
 * ```
 * ┌────────────────────────────┬──────────────────────┐
 * │  artwork                   │  UP NEXT      3 / 12 │
 * │  NOW PLAYING               │  ─────────────────── │
 * │  Track title               │  2  Track            │
 * │  Artist                    │  3  Track            │
 * │                            │  4  Track            │
 * │  ─────────────────────     │                      │
 * │  ⏮   ▶   ⏭                │                      │
 * └────────────────────────────┴──────────────────────┘
 * ```
 *
 * The split is 60/40 rather than even: the left column has to carry artwork large enough to
 * recognise at arm's length, while the queue only needs to be readable, not prominent.
 *
 * The design rules this surface holds to, all of which follow from "the user may be driving":
 * every common action is one tap from here; nothing is more than two taps deep; there are no
 * modal dialogs and no toasts that must be dismissed; no text is smaller than
 * [CarDimensions.MIN_TEXT]; and the transport row never scrolls away.
 */
@OptIn(UnstableApi::class)
@Composable
fun CarModeScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val player: StatefulPlayer = koinInject()

    val currentMediaItem by player.currentMediaItemState.collectAsState()
    val timeline by player.currentTimelineState.collectAsState()
    val queue: List<MediaItem> = timeline.mediaItems

    // Browse and now-playing are peers, toggled in place rather than pushed onto a back stack.
    // Nothing in Car Mode should ever be somewhere you have to navigate *back* out of.
    var tab by rememberSaveable { mutableStateOf( CarTab.NOW_PLAYING ) }

    val isOnline by ConnectivityUtils.isAvailable.collectAsState()

    var showVideo by rememberSaveable { mutableStateOf( false ) }
    val hasVideo = currentMediaItem?.isVideo == true

    val recents by Database.eventTable
                           .recentlyPlayed( 12 )
                           .collectAsState( initial = emptyList() )
    val favourites by Database.songTable
                              .allFavorites( 12 )
                              .collectAsState( initial = emptyList() )

    // Car Mode is the one surface where the theme has no vote. The theme's own hard gate forbids
    // photographic backgrounds here and caps visual depth at two planes, and a driver glancing for
    // under a second cannot be asked to find a control against a moving sky. Declaring Minimal
    // makes that true for *every* skin at once rather than only the restrained ones.
    androidx.compose.runtime.CompositionLocalProvider(
        app.kreate.android.themed.luma.LocalLumaIntensity provides
            app.kreate.android.themed.luma.LumaIntensity.Minimal
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background( LumaColor.Ground )
        ) {
            // Ornament honours the surface intensity, so at Minimal this draws nothing at all.
            SkinOrnamentLayer()

            // A head unit's screen is usually shared with a status bar and, on a tablet, a persistent
            // taskbar. Without inset padding the transport row lands underneath them and the primary
            // control gets clipped — which defeats the one guarantee this mode makes, that play/pause
            // is always reachable.
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {

                // Offline is stated once, inline, and never blocks anything. Whatever is already
                // buffered keeps playing underneath — the deep forward buffer means a dead spot is
                // usually survivable, so the honest message is "connection lost", not "playback
                // failed".
                CarConnectionBanner(
                    visible = !isOnline,
                    message = "No connection — playing from buffer",
                    modifier = Modifier.padding( horizontal = CarDimensions.EDGE, vertical = 8.dp )
                )

                CarTopBar(
                    onExit = onExit,
                    tab = tab,
                    onTabChange = { tab = it },
                    // The toggle only appears when the track actually has video. A control that is
                    // present but inert is worse than no control at all here — it invites a tap, and
                    // a tap that does nothing while driving is a tap spent for no reason.
                    showVideoToggle = tab == CarTab.NOW_PLAYING && hasVideo,
                    showingVideo = showVideo,
                    onToggleVideo = { showVideo = !showVideo }
                )

                if ( tab == CarTab.BROWSE ) {
                    CarBrowse(
                        recents = recents,
                        favourites = favourites,
                        onPlay = { song ->
                            player.forcePlay( song.asMediaItem )
                            // Jump straight to now-playing: choosing something is a complete action,
                            // and leaving the user on the browse screen would make them tap again to
                            // see what they just started.
                            tab = CarTab.NOW_PLAYING
                        },
                        modifier = Modifier.padding( bottom = 24.dp )
                    )
                    return@Column
                }

                // Car Mode is designed for a landscape head unit, but nothing stops someone opening it
                // on a phone held upright. Side-by-side zones do not merely look cramped there — the
                // 60/40 split starves each column so badly that labels wrap to one letter per line and
                // the skip button leaves the screen. Portrait therefore gets its own stacking, not a
                // squeezed version of the landscape one.
                val isLandscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

                val nowPlaying: @Composable () -> Unit = {
                    // Audio and video are the same playback, drawn two ways. Because both halves now
                    // run through one ExoPlayer, switching is just a matter of whether a surface is
                    // attached — the stream never restarts, the position never resets, and the queue is
                    // untouched. That is only possible because the IFrame WebView was retired.
                    if ( showVideo && hasVideo )
                        VideoSurface(
                            player = player,
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio( 16f / 9f, matchHeightConstraintsFirst = true )
                                .clip( RoundedCornerShape( LumaRadius.Large ) )
                        )
                    else
                        CarArtwork(
                            mediaItem = currentMediaItem,
                            modifier = Modifier.fillMaxHeight()
                        )
                }

                if ( isLandscape )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding( horizontal = CarDimensions.EDGE )
                    ) {
                        // Left — what's playing, and the controls for it.
                        //
                        // Order matters: the transport row and metadata claim their natural height
                        // first, and the artwork takes whatever is left over. Controls can therefore
                        // never be pushed off-screen by a large cover — art is decoration, the
                        // transport row is the reason for the mode.
                        Column(
                            modifier = Modifier
                                .weight( 0.6f )
                                .fillMaxHeight()
                                .padding( vertical = 24.dp ),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Art beside metadata, not above it. A centre display is far wider than it
                            // is tall, so stacking wastes the width and starves the artwork of height.
                            Row(
                                modifier = Modifier.weight( 1f ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                nowPlaying()

                                Spacer( Modifier.width( 32.dp ) )

                                CarTrackInfo(
                                    mediaItem = currentMediaItem,
                                    modifier = Modifier.weight( 1f )
                                )
                            }

                            Spacer( Modifier.height( 28.dp ) )

                            // Transport spans the full zone width so the controls stay as far apart as
                            // possible — adjacent targets are what cause mis-taps at speed.
                            CarTransport( player = player )
                        }

                        Spacer( Modifier.width( CarDimensions.EDGE ) )

                        // Right — where we're going next
                        CarQueue(
                            queue = queue,
                            currentIndex = player.currentMediaItemIndex,
                            onJumpTo = { player.seekTo( it, 0L ) },
                            modifier = Modifier
                                .weight( 0.4f )
                                .fillMaxHeight()
                                .padding( vertical = 24.dp )
                        )
                    }
                else
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding( horizontal = CarDimensions.EDGE )
                            .padding( bottom = 16.dp )
                    ) {
                        // Artwork gets a bounded share rather than a weight, so it can never grow at
                        // the expense of the controls below it.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight( 0.34f ),
                            contentAlignment = Alignment.Center
                        ) { nowPlaying() }

                        Spacer( Modifier.height( 20.dp ) )

                        CarTrackInfo( mediaItem = currentMediaItem )

                        Spacer( Modifier.height( 20.dp ) )

                        CarTransport( player = player )

                        Spacer( Modifier.height( 24.dp ) )

                        // Queue sits below the controls: reaching it means scrolling, which is fine
                        // for something you consult, but the transport must never require that.
                        CarQueue(
                            queue = queue,
                            currentIndex = player.currentMediaItemIndex,
                            onJumpTo = { player.seekTo( it, 0L ) },
                            modifier = Modifier.weight( 0.66f )
                        )
                    }
            }
        }
    }
}

/**
 * A thin bar carrying only the exit affordance and connection status.
 *
 * Car Mode is entered and left deliberately, so the way out is always visible and always in the
 * same place — never hidden behind a gesture or a long-press, which are exactly the interactions
 * that fail when the vehicle is moving.
 */
@Composable
private fun CarTopBar(
    onExit: () -> Unit,
    tab: CarTab,
    onTabChange: ( CarTab ) -> Unit,
    showVideoToggle: Boolean,
    showingVideo: Boolean,
    onToggleVideo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding( horizontal = CarDimensions.EDGE, vertical = 16.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CarIconButton(
            icon = R.drawable.chevron_down,
            onClick = onExit
        )

        if ( showVideoToggle ) {
            Spacer( Modifier.width( 16.dp ) )
            CarIconButton(
                icon = if ( showingVideo ) R.drawable.musical_notes else R.drawable.video,
                tint = if ( showingVideo ) LumaColor.Ember else LumaColor.Ink,
                onClick = onToggleVideo
            )
        }

        Spacer( Modifier.weight( 1f ) )

        // Two segments, both always visible and always in the same place. A segmented control
        // rather than a menu: the available destinations are legible without opening anything,
        // and switching is a single tap on a target you can find without reading it.
        CarTab.entries.forEach { entry ->
            CarTabButton(
                label = entry.label,
                selected = tab == entry,
                onClick = { onTabChange( entry ) }
            )
            Spacer( Modifier.width( 12.dp ) )
        }
    }
}

@Composable
private fun CarTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height( CarDimensions.TOUCH_TARGET )
            .clip( RoundedCornerShape( CarDimensions.TOUCH_TARGET / 2 ) )
            .background(
                if ( selected ) LumaColor.Ember else LumaColor.Raised
            )
            .clickable( onClick = onClick )
            .padding( horizontal = 24.dp ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            // Serif, like every other label the user reads in Luma. Car Mode keeps its own *sizes*
            // — those are a safety rule, not a style — but there is no reason for it to speak in a
            // different voice from the app it is part of.
            style = LumaType.Row.copy( fontSize = CarDimensions.MIN_TEXT ),
            color = if ( selected ) LumaColor.Ground else LumaColor.Ink,
            // Never wrap: a two-line "Bro / wse" is how this looked on a narrow screen.
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The two peer surfaces inside Car Mode. */
enum class CarTab( val label: String ) {
    NOW_PLAYING( "Now Playing" ),
    BROWSE( "Browse" )
}

/**
 * Degraded-network status, shown inline and non-blocking.
 *
 * Never a dialog and never a toast that must be dismissed: an interruption that steals focus or
 * demands a tap is genuinely dangerous at speed. This states what is happening and leaves whatever
 * is buffered playing underneath it.
 */
@Composable
fun CarConnectionBanner(
    visible: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip( RoundedCornerShape( LumaRadius.Card ) )
                .background( LumaColor.Alarm.copy( alpha = 0.16f ) )
                .padding( horizontal = 24.dp, vertical = 16.dp ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
                color = LumaColor.Ink
            )
        }
    }
}
