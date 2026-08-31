package it.fast4x.rimusic.ui.components.navigation.header

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import app.kreate.android.R
import app.kreate.android.drawable.AppIcon
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.Button
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.utils.Toaster

/**
 * The Luma mark.
 *
 * Two artworks, chosen by how dark the surface is. The mark is polished chrome with a mean
 * luminance of 183 — bright silver — so on the light skins (Aurora, Vinyl, Terrazzo, Bloom) the
 * original washes out until the top of the L disappears into the background. The graphite variant
 * is the same artwork darkened with its contrast lifted, which keeps the bevel reading as metal
 * rather than flattening it into a silhouette.
 *
 * Tinting was the obvious alternative and is wrong: the chrome *is* the identity, and a solid fill
 * would throw away the thing that makes the mark look like an object.
 */
@Composable
private fun AppLogo(
    navController: NavController,
    context: Context
) {
    Image(
        painter = painterResource(
            if ( colorPalette().isDark ) R.drawable.luma_mark
            else R.drawable.luma_mark_dark
        ),
        contentDescription = "Luma",
        modifier = Modifier.size( 34.dp )
    )
}

/**
 * "Luma", set in the app's own type rather than shipped as artwork.
 *
 * The previous wordmark was a fixed vector, which meant one colour for ten skins. Rendering it as
 * text lets it inherit [colorPalette] and stay legible on every one of them, and it costs an asset
 * rather than adding one.
 */
@Composable
private fun AppLogoText( navController: NavController ) {
    BasicText(
        text = "Luma",
        style = TextStyle(
            fontSize = LumaType.Section.fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = LumaType.Section.fontFamily,
            // Airy tracking suits a mark about sound carrying outward
            letterSpacing = 1.5.sp,
            color = AppBar.contentColor()
        ),
        modifier = Modifier.clickable {
            if ( NavRoutes.home.isHere( navController ) ) return@clickable

            // In short, navigates to [NavRoutes.home] then previous stacks
            // effectively make it the start again
            navController.navigate( NavRoutes.home.name ) {
                popUpTo( navController.graph.findStartDestination().id ) {
                    inclusive = true
                }

                launchSingleTop = true
            }
        }
    )
}

// START
@Composable
fun AppTitle(
    navController: NavController,
    context: Context
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy( 5.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppLogo( navController, context )
        AppLogoText( navController )

        if(Preference.parentalControl())
            Button(
                iconId = R.drawable.shield_checkmark,
                color = AppBar.contentColor(),
                padding = 0.dp,
                size = 20.dp
            ).Draw()

        if (Preference.debugLog())
            BasicText(
                text = stringResource(R.string.info_debug_mode_enabled),
                style = TextStyle(
                    fontSize = LumaType.Numeral.fontSize,
                    fontFamily = LumaType.Numeral.fontFamily,
                    fontWeight = LumaType.Numeral.fontWeight,
                    color = LumaColor.Alarm
                ),
                modifier = Modifier
                    .clickable {
                        Toaster.s( R.string.info_debug_mode_is_enabled )

                        NavRoutes.settings.navigateHere( navController )
                    }
            )
    }
// END
}