package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.colorPalette

@Composable
fun PrimaryButton(
    onClick: () -> Unit,
    @DrawableRes iconId: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(LumaColor.Raised)
            .size(62.dp)
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = null,
            colorFilter = ColorFilter.tint(LumaColor.Ink),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
        )
    }
}
