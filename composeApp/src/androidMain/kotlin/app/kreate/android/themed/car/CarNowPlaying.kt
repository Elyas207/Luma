package app.kreate.android.themed.car

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * Album art for the now-playing track.
 *
 * Sized from **height**, never width. On a landscape centre display the screen is far wider than
 * it is tall, so a square driven by the column width overflows the display and pushes the
 * controls off the bottom. The caller passes a height-bounded modifier (typically
 * `Modifier.weight(1f)`) and [aspectRatio] then derives the width from it — which also means the
 * art shrinks to give the transport row its space rather than competing for it.
 */
@Composable
fun CarArtwork(
    mediaItem: MediaItem?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            // matchHeightConstraintsFirst is the whole point: the caller has already bounded this
            // by height, and without the flag aspectRatio resolves against the (much larger)
            // available width, fails to fit, and collapses to a fraction of the space it was
            // given. That is why the cover rendered postage-stamp sized on a 2560x1600 display.
            .aspectRatio( 1f, matchHeightConstraintsFirst = true )
            .clip( RoundedCornerShape( 24.dp ) )
            .background( LumaColor.Raised ),
        contentAlignment = Alignment.Center
    ) {
        val artwork = mediaItem?.mediaMetadata?.artworkUri?.toString()

        if ( artwork != null )
            Image(
                painter = ImageFactory.rememberAsyncImagePainter( thumbnailUrl = artwork ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
    }
}

/**
 * Track title and artist, under a quiet standing label.
 *
 * The hierarchy is borrowed from in-car and lean-back players (SiriusXM's is the clearest): a
 * small "NOW PLAYING" label, then the title as by far the largest text on screen, then the artist.
 * The label is small precisely because it never changes — it orients you once and then gets out of
 * the way so the eye lands on the title.
 *
 * Titles wrap to two lines rather than marquee-scrolling. Scrolling text makes the driver wait and
 * keep watching to finish reading, which is the opposite of glanceable.
 */
@Composable
fun CarTrackInfo(
    mediaItem: MediaItem?,
    modifier: Modifier = Modifier
) {
    Column( modifier.fillMaxWidth() ) {

        Text(
            text = "NOW PLAYING",
            style = LumaType.Label.copy(
                fontSize = CarDimensions.LABEL_TEXT,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            ),
            color = LumaColor.InkSoft
        )

        Spacer( Modifier.height( 12.dp ) )

        Text(
            text = mediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing",
            style = LumaType.Title.copy(
                fontSize = CarDimensions.TITLE_TEXT
            ),
            color = LumaColor.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer( Modifier.height( 8.dp ) )

        Text(
            text = mediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
            style = LumaType.Row.copy( fontSize = CarDimensions.SUBTITLE_TEXT ),
            color = LumaColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
