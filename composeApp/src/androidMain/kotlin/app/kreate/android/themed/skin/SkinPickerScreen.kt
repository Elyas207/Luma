package app.kreate.android.themed.skin

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * Choosing a skin.
 *
 * Two decisions shape this screen:
 *
 * **Previews are real, not swatches.** Each card draws a miniature interface using the same
 * surface primitives the app uses, so material differences — gloss, metal, paper, ink — are
 * visible before committing. Ten colour chips would have implied ten recolours, which is exactly
 * the impression this system exists to avoid.
 *
 * **Selection applies immediately.** No preview mode, no confirm step. Tapping a skin re-themes
 * the app underneath the picker, so the choice is judged against the real thing. Changing your
 * mind costs one more tap, which is cheaper than a modal asking whether you meant it.
 */
@Composable
fun SkinPickerScreen( modifier: Modifier = Modifier ) {

    var selectedId by Preferences.SKIN

    // Two columns on a phone; more on a tablet, where cards would otherwise be comically wide.
    val columns = if ( LocalConfiguration.current.screenWidthDp >= 720 ) 4 else 2

    Column(
        modifier
            .fillMaxSize()
            .background( LumaColor.Ground )
    ) {
        Column( Modifier.padding( horizontal = 20.dp, vertical = 16.dp ) ) {
            Text(
                text = "Appearance",
                style = LumaType.Title,
                color = LumaColor.Ink
            )
            Spacer( Modifier.height( 4.dp ) )
            Text(
                text = "Ten skins. Each changes colour, surface, motion and shape — not just the accent.",
                style = LumaType.Tile,
                color = LumaColor.InkSoft
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed( columns ),
            contentPadding = PaddingValues( 20.dp ),
            horizontalArrangement = Arrangement.spacedBy( 14.dp ),
            verticalArrangement = Arrangement.spacedBy( 14.dp ),
            modifier = Modifier.fillMaxSize()
        ) {
            items( Skins.ALL, key = { it.id.name } ) { skin ->
                SkinCard(
                    skin = skin,
                    selected = selectedId == skin.id.name,
                    onClick = {
                        // Toggling off returns to the app's legacy palette rather than forcing a
                        // skin on anyone who wants the original look back.
                        selectedId = if ( selectedId == skin.id.name ) "" else skin.id.name
                    }
                )
            }
        }
    }
}

@Composable
private fun SkinCard(
    skin: Skin,
    selected: Boolean,
    onClick: () -> Unit
) {
    // The selected card lifts and gains an accent ring. Animated so selection reads as a change of
    // state rather than a redraw.
    val borderWidth by animateDpAsState(
        targetValue = if ( selected ) 3.dp else 1.dp,
        label = "skinBorder"
    )
    val borderColor by animateColorAsState(
        targetValue = if ( selected ) skin.palette.accent else LumaColor.Raised,
        label = "skinBorderColor"
    )

    Column(
        Modifier
            .clip( RoundedCornerShape( 18.dp ) )
            .clickable( onClick = onClick )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio( 1.35f )
                .clip( RoundedCornerShape( 16.dp ) )
                .border( borderWidth, borderColor, RoundedCornerShape( 16.dp ) )
        ) {
            SkinPreview( skin, Modifier.fillMaxSize() )

            if ( selected )
                Box(
                    Modifier
                        .align( Alignment.TopEnd )
                        .padding( 8.dp )
                        .size( 18.dp )
                        .clip( CircleShape )
                        .background( skin.palette.accent )
                )
        }

        Spacer( Modifier.height( 8.dp ) )

        // 2dp of horizontal breathing room: the app's type styles disable font padding, which
        // clips the first glyph's left bearing and any descenders when a Text sits flush against
        // its bounds. "Absolute" was rendering as "Λbsolute".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding( horizontal = 2.dp )
        ) {
            Text(
                text = skin.displayName,
                style = LumaType.Row,
                color = LumaColor.Ink
            )
            if ( selected ) {
                Spacer( Modifier.size( 6.dp ) )
                Text(
                    text = "· in use",
                    style = LumaType.Meta.copy( fontSize = 11.sp ),
                    color = skin.palette.accent
                )
            }
        }

        Text(
            text = skin.tagline,
            style = LumaType.Meta.copy( lineHeight = 15.sp ),
            color = LumaColor.InkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding( horizontal = 2.dp, vertical = 1.dp )
        )
    }
}
