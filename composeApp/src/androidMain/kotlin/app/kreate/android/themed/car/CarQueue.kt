package app.kreate.android.themed.car

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * Up-next, as a list you jump around with single taps.
 *
 * Two deliberate omissions, both because this is used while driving:
 *
 * - **No drag-to-reorder.** A long-press-then-drag needs sustained attention and a steady hand,
 *   and mis-firing it silently rearranges the queue. Reordering is a parked activity; the normal
 *   player still offers it.
 * - **No per-row overflow menus.** A row does exactly one thing when tapped: play that track.
 *   Nothing on this list can open a submenu, so there is no way to end up somewhere unexpected.
 *
 * Layout follows the "Up Next" pattern used by lean-back readers and players (Bloomberg's is a
 * good example): a quiet section header, then large titles carrying the information. Thumbnails
 * are omitted on purpose — at a glance the title is what identifies a track, and artwork here
 * would compete with the artwork that is already the anchor of the left-hand zone.
 */
@Composable
fun CarQueue(
    queue: List<MediaItem>,
    currentIndex: Int,
    onJumpTo: ( Int ) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Follow the queue as it advances, so "what's next" is always what's on screen without the
    // driver having to scroll to find their place.
    LaunchedEffect( currentIndex ) {
        if ( currentIndex >= 0 && currentIndex < queue.size )
            listState.animateScrollToItem( currentIndex )
    }

    Column( modifier ) {

        Row( verticalAlignment = Alignment.CenterVertically ) {
            Text(
                text = "UP NEXT",
                style = LumaType.Label.copy(
                    fontSize = CarDimensions.LABEL_TEXT,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = LumaColor.InkSoft
            )

            Spacer( Modifier.weight( 1f ) )

            // Position counter — tells you where you are in the queue without counting rows
            if ( queue.isNotEmpty() )
                Text(
                    text = "${currentIndex + 1} / ${queue.size}",
                    style = LumaType.Label.copy( fontSize = CarDimensions.LABEL_TEXT ),
                    color = LumaColor.InkSoft
                )
        }

        Spacer( Modifier.height( 16.dp ) )

        if ( queue.isEmpty() ) {
            CarQueueEmpty()
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy( 8.dp )
        ) {
            itemsIndexed( queue ) { index, item ->
                CarQueueRow(
                    item = item,
                    position = index + 1,
                    isCurrent = index == currentIndex,
                    isPlayed = index < currentIndex,
                    onClick = { onJumpTo( index ) }
                )
            }
        }
    }
}

@Composable
private fun CarQueueRow(
    item: MediaItem,
    position: Int,
    isCurrent: Boolean,
    isPlayed: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height( CarDimensions.QUEUE_ROW_HEIGHT )
            .clip( RoundedCornerShape( 16.dp ) )
            .background(
                if ( isCurrent ) LumaColor.Ember.copy( alpha = 0.18f )
                else LumaColor.Raised
            )
            .clickable( onClick = onClick )
            .padding( horizontal = 20.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$position",
            style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
            // Already-played rows recede rather than disappear — still reachable, clearly behind you
            color = if ( isCurrent ) LumaColor.Ember
                    else LumaColor.InkSoft.copy( alpha = if ( isPlayed ) 0.4f else 1f ),
            modifier = Modifier.width( 44.dp )
        )

        Column( Modifier.weight( 1f ) ) {
            Text(
                text = item.mediaMetadata.title?.toString().orEmpty(),
                style = LumaType.Row.copy(
                    fontSize = CarDimensions.QUEUE_TITLE_TEXT,
                    fontWeight = if ( isCurrent ) FontWeight.Bold else FontWeight.Normal
                ),
                color = if ( isCurrent ) LumaColor.Ink
                        else LumaColor.Ink.copy( alpha = if ( isPlayed ) 0.5f else 1f ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.mediaMetadata.artist?.toString().orEmpty(),
                style = LumaType.Meta.copy( fontSize = CarDimensions.MIN_TEXT ),
                color = LumaColor.InkSoft.copy( alpha = if ( isPlayed ) 0.4f else 1f ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CarQueueEmpty() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Nothing queued",
            style = LumaType.Row.copy( fontSize = CarDimensions.SUBTITLE_TEXT ),
            color = LumaColor.InkSoft
        )
    }
}
