package it.fast4x.rimusic.utils

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.colorPalette

@Composable
fun menuItemColors(): MenuItemColors {
    return MenuItemColors(
        leadingIconColor =  LumaColor.Ember,
        trailingIconColor =  LumaColor.Ember,
        textColor = LumaColor.InkSoft,
        disabledTextColor = LumaColor.Ink,
        disabledLeadingIconColor = LumaColor.Ink,
        disabledTrailingIconColor = LumaColor.Ink,
    )

}