package app.kreate.android.themed.car

import app.kreate.android.themed.luma.LumaRadius

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import app.kreate.android.coil3.ImageFactory
import app.kreate.database.models.Song
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * Car Mode's browse surface: the answer to "change what's playing without digging".
 *
 * Everything here is one tap from playing. There are no folders, no filters, no sort controls and
 * no search — those are all "look at the screen and think" interactions. What is offered instead
 * is the two lists that actually predict what someone wants in a car: what they were just
 * listening to, and what they have explicitly marked as loved.
 *
 * Horizontal rows rather than a vertical grid, because on a landscape display a row shows more
 * items per glance and swiping sideways is a coarser, more forgiving gesture than scrolling a
 * grid. Tiles are large for the same reason the transport controls are.
 */
@Composable
fun CarBrowse(
    recents: List<Song>,
    favourites: List<Song>,
    onPlay: ( Song ) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding( horizontal = CarDimensions.EDGE )
    ) {
        CarShelf(
            title = "JUMP BACK IN",
            songs = recents,
            emptyMessage = "Nothing played yet",
            onPlay = onPlay,
            modifier = Modifier.weight( 1f )
        )

        Spacer( Modifier.height( 24.dp ) )

        CarShelf(
            title = "FAVOURITES",
            songs = favourites,
            emptyMessage = "No favourites yet",
            onPlay = onPlay,
            modifier = Modifier.weight( 1f )
        )
    }
}

/**
 * One titled row of tiles.
 *
 * The shelf keeps its height whether or not it has content, so switching between browse and
 * now-playing never reflows the screen — a layout that jumps around is disorienting when you are
 * only half looking at it.
 */
@Composable
private fun CarShelf(
    title: String,
    songs: List<Song>,
    emptyMessage: String,
    onPlay: ( Song ) -> Unit,
    modifier: Modifier = Modifier
) {
    Column( modifier ) {

        Text(
            text = title,
            style = LumaType.Label.copy(
                fontSize = CarDimensions.LABEL_TEXT,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            ),
            color = LumaColor.InkSoft
        )

        Spacer( Modifier.height( 16.dp ) )

        if ( songs.isEmpty() )
            Box( Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart ) {
                Text(
                    text = emptyMessage,
                    style = LumaType.Row.copy( fontSize = CarDimensions.SUBTITLE_TEXT ),
                    color = LumaColor.InkSoft
                )
            }
        else
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy( 20.dp )
            ) {
                items( songs, key = Song::id ) { song ->
                    CarTile( song = song, onClick = { onPlay( song ) } )
                }
            }
    }
}

/**
 * A single tile. The whole tile is the target — artwork, title and artist all trigger playback,
 * so there is no small hit area to find.
 */
@Composable
private fun CarTile(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width( TILE_SIZE )
            .clickable( onClick = onClick )
    ) {
        Box(
            modifier = Modifier
                // Explicit square rather than derived from the shelf height. Deriving it made the
                // artwork collapse to a fraction of the space available, and a tile you cannot
                // recognise from the driver's seat is not doing its job.
                .size( TILE_SIZE )
                .clip( RoundedCornerShape( LumaRadius.Sleeve ) )
                .background( LumaColor.Raised )
        ) {
            song.cleanThumbnailUrl()?.also { url ->
                Image(
                    painter = ImageFactory.rememberAsyncImagePainter( thumbnailUrl = url ),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer( Modifier.height( 12.dp ) )

        Text(
            text = song.cleanTitle(),
            style = LumaType.Row.copy(
                fontSize = CarDimensions.QUEUE_TITLE_TEXT),
            color = LumaColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width( TILE_SIZE )
        )

        Text(
            text = song.cleanArtistsText(),
            style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
            color = LumaColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width( TILE_SIZE )
        )
    }
}

/**
 * Tile edge length.
 *
 * Sized so a tile *and both its text lines* fit the shelf height on a landscape head unit. At
 * 210dp the artwork fitted but the artist line was clipped off the bottom of every tile, which is
 * the kind of thing that looks like a rendering glitch rather than a layout decision. Titles are
 * pinned to the same width so long names ellipsize rather than stretching a tile and breaking the
 * row's rhythm.
 */
private val TILE_SIZE = 180.dp
