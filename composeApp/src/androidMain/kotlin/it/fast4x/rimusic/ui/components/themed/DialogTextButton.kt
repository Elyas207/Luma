package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaRadius

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val textColor = when {
        !enabled -> LumaColor.InkFaint
        primary -> LumaColor.Ground
        else -> LumaColor.Ink
    }

    BasicText(
        text = text,
        style = LumaType.Meta.color(textColor),
        modifier = modifier
            .clip(RoundedCornerShape( LumaRadius.Full ))
            .background(if (primary) LumaColor.Ember else Color.Transparent)
            //.background(if (primary) colorPalette.accent else colorPalette.background4)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    )
}
