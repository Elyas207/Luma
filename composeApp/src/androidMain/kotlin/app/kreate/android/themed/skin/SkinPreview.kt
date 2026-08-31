package app.kreate.android.themed.skin

import app.kreate.android.themed.luma.LumaRadius

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A miniature of the real interface, rendered in [skin].
 *
 * Colour chips are a poor way to choose a theme: they show the palette but not the *material*, and
 * material is most of what separates these skins. A swatch cannot tell you that Aurora is glossy
 * and Graphite is a machined plate — but a small mock of artwork, a transport row and two list
 * rows can, because it is drawn with the same primitives the real screens use.
 *
 * Deliberately static. Ten animated previews on one screen would cost more than the feature is
 * worth, and the motion personality is better felt after applying than watched in a thumbnail.
 */
@Composable
fun SkinPreview(
    skin: Skin,
    modifier: Modifier = Modifier
) {
    ProvideSkin( skin ) {
        Box(
            modifier
                .clip( RoundedCornerShape( skin.shape.medium ) )
                .background( skin.palette.background0 )
        ) {
            // Ornament, at the same treatment the real screens get
            when ( skin.ornament ) {
                SkinOrnament.AERO_SKY -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf( Color( 0xFF7EC8F0 ), Color( 0xFFBFE6F7 ), skin.palette.background0 )
                        )
                    )
                )
                SkinOrnament.GRADIENT_WASH -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf( skin.palette.background2, skin.palette.background0 )
                        )
                    )
                )
                else -> SkinOrnamentLayer()
            }

            Column( Modifier.fillMaxSize().padding( 10.dp ) ) {

                // Artwork + title block
                Row( verticalAlignment = Alignment.CenterVertically ) {
                    SkinnedSurface(
                        modifier = Modifier.size( 34.dp ),
                        elevated = true,
                        cornerRadius = skin.shape.small
                    )

                    Spacer( Modifier.width( 8.dp ) )

                    Column {
                        MiniBar( skin.palette.text, 46.dp )
                        Spacer( Modifier.height( 4.dp ) )
                        MiniBar( skin.palette.textSecondary, 30.dp )
                    }
                }

                Spacer( Modifier.height( 10.dp ) )

                // Transport: the accent is the only saturated thing, exactly as in the app
                Row( verticalAlignment = Alignment.CenterVertically ) {
                    Box(
                        Modifier
                            .size( 18.dp )
                            .clip( CircleShape )
                            .background( skin.palette.accent )
                    )
                    Spacer( Modifier.width( 6.dp ) )
                    Box(
                        Modifier
                            .height( 4.dp )
                            .weight( 1f )
                            .clip( RoundedCornerShape( LumaRadius.Hairline ) )
                            .background( skin.palette.background3 )
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth( 0.45f )
                                .fillMaxHeight()
                                .clip( RoundedCornerShape( LumaRadius.Hairline ) )
                                .background( skin.palette.accent )
                        )
                    }
                }

                Spacer( Modifier.height( 10.dp ) )

                // Two list rows, so the surface treatment is visible at a glance
                repeat( 2 ) {
                    SkinnedSurface(
                        modifier = Modifier.fillMaxWidth().height( 18.dp ),
                        cornerRadius = skin.shape.small
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding( horizontal = 6.dp ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiniBar( skin.palette.text, 34.dp )
                        }
                    }
                    Spacer( Modifier.height( 5.dp ) )
                }
            }
        }
    }
}

@Composable
private fun MiniBar( color: Color, width: androidx.compose.ui.unit.Dp ) =
    Box(
        Modifier
            .width( width )
            .height( 5.dp )
            .clip( RoundedCornerShape( LumaRadius.Hairline ) )
            .background( color.copy( alpha = 0.75f ) )
    )
