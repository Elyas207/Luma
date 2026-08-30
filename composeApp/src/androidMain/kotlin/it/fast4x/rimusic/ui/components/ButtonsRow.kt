package it.fast4x.rimusic.ui.components

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.typography

/**
 * A row of filters.
 *
 * These were Material `FilterChip`s: outlined pills in a horizontal strip. The outline is the most
 * recognisable signature in the whole stock component set — it is what makes an app look like every
 * other app — and four outlined boxes are visually heavier than the list they are filtering, which
 * inverts the hierarchy on a screen whose job is to show music.
 *
 * The outline is gone. The selected filter is a quiet filled pill and the rest are plain text, so
 * the row reads as one line of options with one of them lit rather than as four competing objects.
 *
 * It stays visibly *different* from the section rail in [app.kreate.android.themed.library.LibraryScaffold],
 * which never draws a container: a rail item takes you somewhere, a filter changes what you are
 * looking at, and the filled pill is what marks that difference without a label explaining it.
 */
@Composable
private fun FilterRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: ( Int ) -> Unit,
    modifier: Modifier = Modifier
) = Row(
    modifier = modifier.horizontalScroll( rememberScrollState() ),
    horizontalArrangement = Arrangement.spacedBy( 4.dp )
) {
    labels.forEachIndexed { index, label ->

        val isSelected = index == selectedIndex

        val background by animateColorAsState(
            targetValue = if ( isSelected ) LumaColor.Raised else Color.Transparent,
            label = "filter-background"
        )
        val content by animateColorAsState(
            targetValue = if ( isSelected ) LumaColor.Ink else LumaColor.InkSoft,
            label = "filter-content"
        )

        Text(
            text = label,
            // Selection is carried by the fill and the ink, never by weight: the display serif
            // ships in one weight, so asking for SemiBold makes the platform synthesise it and the
            // label comes out smeared rather than emphasised.
            style = LumaType.Tile,
            color = content,
            modifier = Modifier
                .clip( RoundedCornerShape( 18.dp ) )
                .background( background )
                .clickable { onSelect( index ) }
                .padding( horizontal = 16.dp, vertical = 9.dp )
        )
    }
}

@Composable
fun <E> ButtonsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
) = FilterRow(
    labels = chips.map { it.second },
    selectedIndex = chips.indexOfFirst { it.first == currentValue },
    onSelect = { onValueUpdate( chips[it].first ) },
    modifier = modifier
)

@Composable
fun ButtonsRow(
    chips: List<BuiltInPlaylist>,
    currentValue: BuiltInPlaylist,
    onValueUpdate: (BuiltInPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) = FilterRow(
    labels = chips.map { it.text },
    selectedIndex = chips.indexOf( currentValue ),
    onSelect = { onValueUpdate( chips[it] ) },
    modifier = modifier
)
