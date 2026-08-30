package it.fast4x.rimusic.ui.components.themed

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.colorPalette

/**
 * The overflow menu, and every menu built on it.
 *
 * It was drawing on a transparent container, so it inherited Material's default surface and sat on
 * the screen as a plain grey rectangle with default sans labels — the one place in the app where
 * stock Android showed through completely. It is also reached from every screen, which makes it one
 * of the most-seen surfaces there is.
 */
class DropdownMenu(
    val expanded: Boolean,
    // Transparent meant "whatever Material decides", which was a grey that belongs to no palette
    // here. The raised surface is the same one the mini player and every sheet sits on.
    val containerColor: Color = LumaColor.Raised,
    val modifier: Modifier = Modifier,
    val onDismissRequest: () -> Unit
) {

    private val _components: MutableList<@Composable () -> Unit> = mutableListOf()

    @Composable
    fun components() = remember { _components }

    @Composable
    fun add( item: Item) = _components.add { item.Draw() }

    @Composable
    fun add( component: @Composable () -> Unit) = _components.add( component )

    @Composable
    fun Draw() {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = containerColor,
            modifier = modifier,
            shape = androidx.compose.foundation.shape.RoundedCornerShape( 18.dp ),
            content = { components().forEach { it() } }
        )
    }

    class Item(
        val iconId: Int,
        val textId: Int,
        val size: Dp = 24.dp,
        val padding: Dp = Dp.Hairline,
        val colors: MenuItemColors? = null,
        val modifier: Modifier = Modifier,
        val onClick: () -> Unit
    ) {

        companion object {

            @Composable
            fun colors(): MenuItemColors {
                return MenuItemColors(
                    leadingIconColor =  LumaColor.Ember,
                    trailingIconColor =  LumaColor.Ember,
                    textColor = LumaColor.Ink,
                    disabledTextColor = LumaColor.Ink,
                    disabledLeadingIconColor = LumaColor.Ink,
                    disabledTrailingIconColor = LumaColor.Ink,
                )
            }
        }

        @Composable
        fun Draw() {
            val icon: @Composable () -> Unit = {
                Icon(
                    painter = painterResource( iconId ),
                    contentDescription = null,
                    modifier = modifier.size( 24.dp )
                )
            }

            DropdownMenuItem(
                enabled = true,
                colors = colors ?: colors(),
                // Serif, so a menu reads as part of Luma rather than as a system popup.
                text = {
                    Text(
                        text = stringResource( textId ),
                        style = LumaType.Tile
                    )
                },
                leadingIcon = icon,
                onClick = onClick
            )
        }
    }
}