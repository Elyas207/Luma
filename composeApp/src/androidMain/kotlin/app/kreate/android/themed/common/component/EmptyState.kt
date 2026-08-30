package app.kreate.android.themed.common.component

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * The shared "there is nothing here" surface.
 *
 * An empty list previously rendered as a blank screen, which is indistinguishable from a screen
 * that is still loading or one that has broken. Three things fix that, and every good empty state
 * has all three:
 *
 * 1. **An icon**, so the area reads as deliberate rather than as a failure to draw.
 * 2. **A headline naming the state**, so the user knows the app is working and the list is simply
 *    empty.
 * 3. **A way out.** This is the part most often skipped. Telling someone their favourites are
 *    empty is not useful; offering them the action that fills it is. [action] is therefore
 *    strongly encouraged wherever a sensible next step exists.
 */
@Composable
fun EmptyState(
    iconId: Int,
    titleId: Int,
    descriptionId: Int? = null,
    actionLabelId: Int? = null,
    onAction: ( () -> Unit )? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding( horizontal = 32.dp ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size( 88.dp )
                .clip( RoundedCornerShape( 24.dp ) )
                .background( LumaColor.Raised ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource( iconId ),
                contentDescription = null,
                colorFilter = ColorFilter.tint( LumaColor.InkSoft ),
                modifier = Modifier.size( 40.dp )
            )
        }

        Spacer( Modifier.height( 24.dp ) )

        Text(
            text = stringResource( titleId ),
            style = LumaType.Section,
            color = LumaColor.Ink,
            textAlign = TextAlign.Center
        )

        if ( descriptionId != null ) {
            Spacer( Modifier.height( 8.dp ) )

            Text(
                text = stringResource( descriptionId ),
                style = LumaType.Tile,
                color = LumaColor.InkSoft,
                textAlign = TextAlign.Center
            )
        }

        if ( actionLabelId != null && onAction != null ) {
            Spacer( Modifier.height( 24.dp ) )

            Box(
                modifier = Modifier
                    .clip( RoundedCornerShape( 24.dp ) )
                    .background( LumaColor.Ember )
                    .clickable( onClick = onAction )
                    .padding( horizontal = 28.dp, vertical = 14.dp )
            ) {
                Text(
                    text = stringResource( actionLabelId ),
                    style = LumaType.Tile,
                    color = LumaColor.Ground
                )
            }
        }
    }
}
