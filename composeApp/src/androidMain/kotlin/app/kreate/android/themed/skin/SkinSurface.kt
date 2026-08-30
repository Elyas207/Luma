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
                    // The specular sweep, taken from the references rather than from the usual
                    // imitation of them.
                    //
                    // Sampling `images 4.jpg` down its vertical axis gives luminance 153 / 229 / 79
                    // across top / middle / bottom: the brightest band sits in the *upper middle*
                    // and falls away in both directions, over a base that is markedly darker than
                    // the top. The previous recipe here — brightest at the very top, gone by the
                    // midpoint, a barely-there base — is the shape modern glassmorphism uses, and
                    // it is what makes a fake Aero surface read as a flat tinted rectangle.
                    //
                    // The band is also kept clear of the lower half, because that is where labels
                    // sit and a white highlight running under white text is this aesthetic's
                    // signature legibility failure.
                    SkinSurface.GLOSS -> Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.00f to Color.White.copy( alpha = material.glossStrength * 0.55f ),
                                0.38f to Color.White.copy( alpha = material.glossStrength ),
                                0.52f to Color.White.copy( alpha = material.glossStrength * 0.10f ),
                                0.75f to Color.Transparent,
                                1.00f to Color.Black.copy( alpha = 0.14f )
                            )
                        )
                        // The bevel: a light edge where the light falls and a darker one away from
                        // it. Cheaper than the gloss and, in the references, doing more of the work
                        // — it is what makes a control read as an object rather than as a fill.
                        drawRect(
                            brush = Brush.linearGradient(
                                0.00f to Color.White.copy( alpha = 0.40f ),
                                0.40f to Color.Transparent,
                                1.00f to Color.Black.copy( alpha = 0.12f ),
                                start = Offset.Zero,
                                end = Offset( size.width, size.height )
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke( width = 2f )
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
    val intensity = app.kreate.android.themed.luma.lumaIntensity()

    // Ornament is a property of the *surface*, not of the skin. Until this check existed a skin's
    // backdrop reached every surface it was drawn on — including Car Mode, where the theme's own
    // hard gate forbids photography, and including the space behind body text (findings 16 and 41).
    // Minimal draws nothing at all; Reduced keeps the cheap procedural washes but never photography.
    if ( intensity == app.kreate.android.themed.luma.LumaIntensity.Minimal ) return

    val ornament =
        if ( skin.ornament == SkinOrnament.AERO_SKY && !intensity.allowsPhotography )
            SkinOrnament.GRADIENT_WASH
        else
            skin.ornament

    when ( ornament ) {
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
