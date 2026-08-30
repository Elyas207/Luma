package app.kreate.android.themed.handoff

import app.kreate.android.themed.luma.LumaRadius

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import app.kreate.android.BuildConfig
import app.kreate.android.service.handoff.Handoff
import app.kreate.android.service.player.StatefulPlayer
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.mediaItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * "Continue on another device."
 *
 * One screen, one square, no setup. The device you're leaving shows a code; the device you're
 * moving to reads it and picks up mid-track. There is nothing to pair, nothing to log into, and no
 * network involved — see [Handoff] for why the payload lives in the image itself.
 *
 * The screen deliberately does not ask *which* device, or *what* to transfer. Both questions have
 * one sensible answer almost always (the one in front of you; what's playing), and asking would
 * turn a two-second action into a form.
 */
@Composable
fun HandoffScreen( modifier: Modifier = Modifier ) {

    val player: StatefulPlayer = koinInject()
    val timeline by player.currentTimelineState.collectAsState()
    val queue: List<MediaItem> = timeline.mediaItems

    val payload = remember( queue, player.currentMediaItemIndex ) {
        Handoff.encode(
            items = queue.map( MediaItem::mediaId ),
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background( LumaColor.Ground )
            .padding( 24.dp ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Continue on another device",
            style = LumaType.Section,
            color = LumaColor.Ink
        )

        Spacer( Modifier.height( 8.dp ) )

        Text(
            text = if ( payload.isEmpty() )
                       "Start playing something first, then bring this up to move it across."
                   else
                       "Open ${BuildConfig.APP_NAME} on the other device and scan this. " +
                       "Your queue and position travel with it — no account, no internet.",
            style = LumaType.Tile,
            color = LumaColor.InkSoft,
            modifier = Modifier.padding( horizontal = 8.dp )
        )

        Spacer( Modifier.height( 28.dp ) )

        if ( payload.isNotEmpty() )
            QrCode( payload )

        Spacer( Modifier.height( 24.dp ) )

        if ( payload.isNotEmpty() ) {
            val carried = queue.size - player.currentMediaItemIndex
            Text(
                text = "$carried ${if ( carried == 1 ) "track" else "tracks"} · resumes where you are",
                style = LumaType.Meta,
                color = LumaColor.InkSoft
            )
        }
    }
}

/**
 * Renders [payload] as a QR bitmap.
 *
 * Encoding happens off the main thread — for a full queue this is a few milliseconds, but it scales
 * with payload size and has no business blocking a frame. High error correction is deliberate: this
 * code will be read at an angle, from a phone held over a car dashboard, possibly in sunlight.
 */
@Composable
private fun QrCode( payload: String, sizePx: Int = 720 ) {

    val bitmap by produceState<Bitmap?>( initialValue = null, payload ) {
        value = withContext( Dispatchers.Default ) {
            runCatching {
                val matrix = QRCodeWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    sizePx,
                    sizePx,
                    mapOf(
                        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                        EncodeHintType.MARGIN to 1
                    )
                )

                Bitmap.createBitmap( sizePx, sizePx, Bitmap.Config.ARGB_8888 ).apply {
                    for ( x in 0 until sizePx )
                        for ( y in 0 until sizePx )
                            setPixel(
                                x, y,
                                if ( matrix[x, y] ) android.graphics.Color.BLACK
                                else android.graphics.Color.WHITE
                            )
                }
            }.getOrNull()
        }
    }

    Box(
        Modifier
            .size( 280.dp )
            // Always on white, never on the skin's background. A QR code on a dark or tinted
            // surface is measurably harder for a camera to lock onto, and this is the one element
            // in the app where legibility to a *machine* outranks visual consistency.
            .clip( RoundedCornerShape( LumaRadius.Card ) )
            .background( androidx.compose.ui.graphics.Color.White )
            .padding( 12.dp ),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.also {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Handoff code",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun <T> remember( key1: Any?, key2: Any?, calculation: () -> T ): T =
    androidx.compose.runtime.remember( key1, key2 ) { calculation() }
