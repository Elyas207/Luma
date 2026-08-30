package app.kreate.android.themed.skin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The skin in force. Defaults to [Skins.OBSIDIAN] so anything reading this outside a themed tree
 * still renders sensibly rather than crashing.
 */
val LocalSkin = staticCompositionLocalOf { Skins.OBSIDIAN }

@Composable
fun ProvideSkin( skin: Skin, content: @Composable () -> Unit ) =
    CompositionLocalProvider( LocalSkin provides skin, content = content )

/**
 * A surface drawn in the current skin's material.
 *
 * This is the single place where "material" stops being a token and becomes pixels. Every skin
 * feeds the same call and gets a different object back: Aurora returns a glossy lozenge with a
 * specular sweep, Graphite a bordered metal plate, Obsidian a plain black rectangle with a
 * hairline. Callers never branch on skin — they ask for a surface and get the right one.
 *
 * @param elevated raises the surface one step, tinted by [SkinMaterial.elevationTint]
 */
@Composable
fun SkinnedSurface(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    cornerRadius: Dp? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val skin = LocalSkin.current
    val material = skin.material
    val radius = cornerRadius ?: skin.shape.medium

    val base = if ( elevated ) skin.palette.background2 else skin.palette.background1
    val tinted =
        if ( elevated ) base.lightenBy( material.elevationTint )
        else base

    val shape = RoundedCornerShape( radius )

    Box(
        modifier = modifier
            .clip( shape )
            .background( tinted.copy( alpha = material.translucency ) )
            .then(
                if ( material.borderWidth.value > 0f )
                    Modifier.border( material.borderWidth, material.borderColor, shape )
                else Modifier
            )
            .then(
                when ( material.surface ) {
                    // A real specular sweep: bright at the very top, gone by the midpoint. This is
                    // the single detail that makes a surface read as glass rather than as a colour.
                    SkinSurface.GLOSS -> Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy( alpha = material.glossStrength ),
                                0.45f to Color.White.copy( alpha = material.glossStrength * 0.15f ),
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy( alpha = 0.10f )
                            )
                        )
                    }

                    // Directional sheen across the diagonal — machined rather than wet.
                    SkinSurface.METAL -> Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.White.copy( alpha = 0.06f ),
                                0.5f to Color.Transparent,
                                1f to Color.Black.copy( alpha = 0.06f ),
                                start = Offset.Zero,
                                end = Offset( size.width, size.height )
                            )
                        )
                    }

                    else -> Modifier
                }
            ),
        content = content
    )
}

/** Lift a colour towards white by [amount], used for elevation tinting. */
internal fun Color.lightenBy( amount: Float ): Color =
    Color(
        red = ( red + ( 1f - red ) * amount ).coerceIn( 0f, 1f ),
        green = ( green + ( 1f - green ) * amount ).coerceIn( 0f, 1f ),
        blue = ( blue + ( 1f - blue ) * amount ).coerceIn( 0f, 1f ),
        alpha = alpha
    )

/**
 * The ornament layer, drawn behind content.
 *
 * Kept deliberately cheap: procedural drawing only, no bitmaps except Aurora's sky, and nothing
 * that animates per-frame unless the skin explicitly asks for it. An ornament that costs frames is
 * worse than no ornament.
 */
@Composable
fun SkinOrnamentLayer( modifier: Modifier = Modifier ) {
    val skin = LocalSkin.current

    when ( skin.ornament ) {
        SkinOrnament.NONE -> Unit

        SkinOrnament.GRADIENT_WASH -> Box(
            modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf( skin.palette.background1, skin.palette.background0 )
                )
            )
        )

        SkinOrnament.GRAIN -> Box(
            modifier.fillMaxSize().drawWithContent {
                drawContent()
                // Deterministic sparse speckle — cheap, and stable across recompositions so it
                // never shimmers.
                val step = 7f
                var y = 0f
                var seed = 1
                while ( y < size.height ) {
                    var x = ( seed * 13 % 7 ).toFloat()
                    while ( x < size.width ) {
                        drawCircle(
                            color = Color.Black.copy( alpha = 0.02f ),
                            radius = 0.7f,
                            center = Offset( x, y )
                        )
                        x += step * 3
                    }
                    y += step
                    seed++
                }
            }
        )

        SkinOrnament.SCANLINES -> Box(
            modifier.fillMaxSize().drawWithContent {
                drawContent()
                var y = 0f
                while ( y < size.height ) {
                    drawRect(
                        color = Color.Black.copy( alpha = 0.06f ),
                        topLeft = Offset( 0f, y ),
                        size = androidx.compose.ui.geometry.Size( size.width, 1f )
                    )
                    y += 3f
                }
            }
        )

        SkinOrnament.TESSELLATION -> Box(
            modifier.fillMaxSize().drawWithContent {
                drawContent()
                // An eight-point star lattice, drawn as overlapping rotated squares.
                val cell = 120f
                val stroke = skin.palette.accent.copy( alpha = 0.05f )
                var y = 0f
                while ( y < size.height + cell ) {
                    var x = 0f
                    while ( x < size.width + cell ) {
                        val c = Offset( x, y )
                        val r = cell * 0.36f
                        for ( i in 0 until 4 ) {
                            val a = ( Math.PI / 4 ) * i
                            drawLine(
                                color = stroke,
                                start = Offset(
                                    c.x - ( r * kotlin.math.cos( a ) ).toFloat(),
                                    c.y - ( r * kotlin.math.sin( a ) ).toFloat()
                                ),
                                end = Offset(
                                    c.x + ( r * kotlin.math.cos( a ) ).toFloat(),
                                    c.y + ( r * kotlin.math.sin( a ) ).toFloat()
                                ),
                                strokeWidth = 1f
                            )
                        }
                        x += cell
                    }
                    y += cell
                }
            }
        )

        // Aurora's sky is a bitmap and is handled by its own composable, which needs asset access.
        SkinOrnament.AERO_SKY -> AeroSky( modifier )
    }
}
