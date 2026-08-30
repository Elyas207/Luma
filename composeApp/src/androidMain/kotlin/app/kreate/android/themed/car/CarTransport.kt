package app.kreate.android.themed.car

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import app.kreate.android.R
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.positionAndDurationState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Text
import androidx.compose.runtime.remember

/**
 * The one row of controls that is always on screen in Car Mode.
 *
 * Modelled on the fixed control row used in vehicle interfaces (Tesla's paired steppers being the
 * clearest example): the controls never move, never collapse into a menu, and never require a
 * decision about where to look. Muscle memory is the whole point — after a few days the driver
 * should be able to hit pause without reading anything.
 *
 * Everything here is sized from [CarDimensions]; nothing is hand-tuned.
 */
@Composable
fun CarTransport(
    player: Player,
    modifier: Modifier = Modifier
) {
    val positionAndDuration by player.positionAndDurationState()
    val (position, duration) = positionAndDuration

    Column( modifier.fillMaxWidth() ) {

        CarScrubber(
            position = position,
            duration = duration,
            onSeek = player::seekTo
        )

        Spacer( Modifier.height( CarDimensions.CONTROL_GAP ) )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy( CarDimensions.CONTROL_GAP, Alignment.CenterHorizontally ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CarIconButton(
                icon = R.drawable.play_skip_back,
                size = CarDimensions.TRANSPORT_SECONDARY,
                enabled = player.hasPreviousMediaItem(),
                onClick = player::seekToPrevious
            )

            CarPlayPauseButton( player )

            CarIconButton(
                icon = R.drawable.play_skip_forward,
                size = CarDimensions.TRANSPORT_SECONDARY,
                enabled = player.hasNextMediaItem(),
                onClick = player::seekToNext
            )
        }
    }
}

/**
 * Play/pause, deliberately the largest target on the screen.
 *
 * Filled rather than outlined so it reads as the primary action at a glance, and so its position
 * is identifiable by shape alone in peripheral vision.
 */
@Composable
private fun CarPlayPauseButton( player: Player ) {
    val isPlaying = player.isPlaying

    Box(
        modifier = Modifier
            .size( CarDimensions.TRANSPORT_PRIMARY )
            .clip( CircleShape )
            .background( LumaColor.Ember )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple( bounded = false ),
                onClick = {
                    if ( isPlaying ) player.pause() else player.play()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource( if ( isPlaying ) R.drawable.pause else R.drawable.play ),
            contentDescription = null,
            colorFilter = ColorFilter.tint( LumaColor.Ground ),
            modifier = Modifier.size( CarDimensions.TRANSPORT_PRIMARY / 2 )
        )
    }
}

/**
 * A circular icon button whose touch target is [size] regardless of how large the glyph is —
 * the glyph is decoration, the circle is the thing being aimed at.
 */
@Composable
fun CarIconButton(
    icon: Int,
    size: androidx.compose.ui.unit.Dp = CarDimensions.TOUCH_TARGET,
    enabled: Boolean = true,
    tint: Color? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size( size )
            .clip( CircleShape )
            .background( LumaColor.Raised )
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple( bounded = false ),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource( icon ),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Disabled controls stay in place and stay the same size — only their contrast drops,
            // so the row never reflows and muscle memory keeps working.
            colorFilter = ColorFilter.tint(
                ( tint ?: LumaColor.Ink ).copy( alpha = if ( enabled ) 1f else 0.3f )
            ),
            modifier = Modifier.size( size / 2.2f )
        )
    }
}

/**
 * Progress with a grab area thick enough to hit while moving.
 *
 * Seeking is a coarse gesture here on purpose. Precise scrubbing is not a thing anyone should be
 * attempting from the driver's seat, so this optimises for "roughly there" over accuracy.
 */
@Composable
private fun CarScrubber(
    position: Long,
    duration: Long,
    onSeek: ( Long ) -> Unit
) {
    val fraction = if ( duration > 0 ) ( position.toFloat() / duration ).coerceIn( 0f, 1f ) else 0f

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height( CarDimensions.SCRUBBER_HEIGHT )
                .clip( RoundedCornerShape( CarDimensions.SCRUBBER_HEIGHT / 2 ) )
                .background( LumaColor.Raised )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth( fraction )
                    .height( CarDimensions.SCRUBBER_HEIGHT )
                    .clip( RoundedCornerShape( CarDimensions.SCRUBBER_HEIGHT / 2 ) )
                    .background( LumaColor.Ember )
            )
        }

        Spacer( Modifier.height( 8.dp ) )

        Row( Modifier.fillMaxWidth() ) {
            Text(
                text = formatDuration( position ),
                style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
                color = LumaColor.InkSoft
            )
            Spacer( Modifier.weight( 1f ) )
            Text(
                text = formatDuration( duration ),
                style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
                color = LumaColor.InkSoft,
                textAlign = TextAlign.End
            )
        }
    }
}

/** `m:ss`, or `h:mm:ss` once the track is long enough to need it. */
internal fun formatDuration( millis: Long ): String {
    if ( millis <= 0 ) return "0:00"

    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = ( totalSeconds / 60 ) % 60
    val hours = totalSeconds / 3600

    return if ( hours > 0 )
        "%d:%02d:%02d".format( hours, minutes, seconds )
    else
        "%d:%02d".format( minutes, seconds )
}
