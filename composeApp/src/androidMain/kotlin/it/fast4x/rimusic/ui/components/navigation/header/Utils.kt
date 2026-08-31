package it.fast4x.rimusic.ui.components.navigation.header

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode

@Composable
/**
 * Every icon in the app header.
 *
 * A bare glyph floating on the background is the most anonymous control there is — it belongs to no
 * design system because it has no form of its own. Luma's controls are rings, so these become rings
 * too, which also matches the transport on the player and the search control on home. One ring is a
 * component; the same ring in four places is a language.
 */
internal fun HeaderIcon(
    iconId: Int,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
    // Rings carry no text, so without this a screen reader announces two unlabelled buttons in the
    // header of every screen in the app.
    contentDescription: String = "",
    onClick: () -> Unit
) = app.kreate.android.themed.luma.LumaRingButton(
    iconRes = iconId,
    contentDescription = contentDescription,
    onClick = onClick,
    diameter = 44.dp,
    modifier = Modifier.padding( horizontal = 3.dp )
)

internal class Preference {

    internal companion object {

        @Composable
        fun parentalControl(): Boolean = Preferences.PARENTAL_CONTROL.value

        @Composable
        fun debugLog(): Boolean = Preferences.RUNTIME_LOG.value

        @Composable
        fun colorTheme(): ColorPaletteMode = Preferences.THEME_MODE.value
    }
}

internal class AppBar {

    internal companion object {

        /**
         * Foreground colour for the app bar.
         *
         * Asks the palette whether it is dark rather than asking the *theme mode preference*,
         * which is what this used to do. Those two agree only when the palette came from that
         * preference — a skin, a cover-derived dynamic palette or Material You can all produce a
         * light palette while the mode still reads "Dark", and the bar then drew white on white.
         *
         * Deferring to `LumaColor.Ink` also means the bar inherits whatever contrast the
         * active palette already defines, instead of hard-coding a colour that only suits half of
         * them.
         */
        @Composable
        fun contentColor(): Color = LumaColor.Ink
    }
}