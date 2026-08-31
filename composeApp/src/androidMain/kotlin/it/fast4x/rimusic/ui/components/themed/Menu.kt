package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

@Composable
inline fun Menu(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .padding(top = 48.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .background(LumaColor.Raised)
            .padding(top = 2.dp)
            .padding(vertical = 8.dp)
            .navigationBarsPadding(),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MenuEntry(
    painter: Painter,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 24.dp)
    ) {
        // Quieter and a little larger than the upstream 15.dp black glyph: in this language the
        // serif label carries the row and the icon is a marker beside it, not the loudest thing in
        // it.
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(LumaColor.InkSoft),
            modifier = Modifier
                .size(19.dp)
        )

        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1f)
        ) {
            // `Meta` is the 14sp sans used for supporting lines, so every menu in the app read as
            // stock Material with the palette swapped. Menu entries are things you pick, and in
            // this language anything you pick is set in the display serif.
            BasicText(
                text = text,
                style = LumaType.Row
            )

            secondaryText?.let { secondaryText ->
                BasicText(
                    text = secondaryText,
                    style = LumaType.Meta
                )
            }
        }

        trailingContent?.invoke()
    }
}

@Composable
fun MenuEntry(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    secondaryText: String? = null,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    MenuEntry(
        painterResource( icon ),
        text,
        onClick,
        onLongClick,
        secondaryText,
        enabled,
        trailingContent
    )
}
