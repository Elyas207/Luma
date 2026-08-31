package app.kreate.android.themed.luma

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The surface layer: radius, depth, intensity, and the gloss recipe.
 *
 * This exists because the audit counted **17 distinct corner radii across 92 uses and zero
 * elevation anywhere** in 589 files (`docs/audit/00-inventory.md` §4). Colour and type were already
 * centralised; these two were not, which is why every card had a hand-picked corner and why the app
 * could not express a glossy, layered aesthetic at all — gloss, bevel and shadow had nothing to
 * hang on.
 */

// ---------------------------------------------------------------------------- radius

/**
 * One ordered radius scale. Five steps, and a screen should rarely need more than two of them.
 *
 * The values are not arbitrary: the Aero references use a *tight* corner on panels
 * (`docs/theme/00-aero-study.md` — roughly 2-3% of panel width, i.e. 4-8dp at these sizes) and a
 * full round only on handles. A 24dp "soft card" corner is a 2020s convention, not a 2000s one, so
 * the scale deliberately weights small.
 */
object LumaRadius {
    /** Dividers, inline chips, progress tracks. */
    val Hairline: Dp = 2.dp
    /** Small inline shapes. */
    val Tight: Dp = 4.dp
    /** The workhorse: buttons, fields, panels, list surfaces. */
    val Panel: Dp = 8.dp
    /** Cards and rows that should read as a distinct object. */
    val Card: Dp = 16.dp
    /** Artwork sleeves. */
    val Sleeve: Dp = 20.dp
    /** Sheets and large containers. */
    val Large: Dp = 24.dp
    /** Anything fully rounded. Large enough to survive any sane control height. */
    val Full: Dp = 999.dp
}

// ---------------------------------------------------------------------------- depth

/**
 * The shadow scale. Four steps, because "objects float above a surface" is one of the four things
 * carrying this aesthetic and an app with one shadow value reads as flat no matter how glossy it is.
 *
 * Deliberately expressed as elevation *levels* rather than raw dp so that a screen asks for
 * "floating" rather than for "6dp", and so Car Mode can cap the whole app at two levels
 * (`LumaIntensity.Minimal`) in one place rather than per component.
 */
enum class LumaDepth( val elevation: Dp ) {
    Flat( 0.dp ),
    /** Rests on the surface: list rows, inline chips. */
    Resting( 2.dp ),
    /** Floats: cards, the mini player, primary buttons. */
    Floating( 8.dp ),
    /** Above everything: sheets, dialogs, menus. */
    Overlay( 20.dp )
}

// ---------------------------------------------------------------------------- intensity

/**
 * How much of the theme a surface is allowed to wear.
 *
 * The theme brief asks for three intensities, and the audit found the mechanism that makes them
 * necessary: ornament was being applied globally, so a skin's photographic backdrop reached Car
 * Mode and reached behind body text (`01-ux-findings.md` X4, findings 16 and 41). Intensity is a
 * property of the *surface*, not of the skin, so every skin gets a calm Car Mode and a calm reading
 * surface rather than only the restrained ones.
 */
enum class LumaIntensity {
    /** Photographic backdrop, gloss, ambient motion. Home, library, discovery, onboarding. */
    Full,
    /** Gradients and gloss on controls; quiet background; no photography behind text. */
    Reduced,
    /** Palette and control language only. No imagery, no gloss, at most two depth levels. */
    Minimal;

    val allowsPhotography: Boolean get() = this == Full
    val allowsGloss: Boolean get() = this != Minimal
    val allowsAmbientMotion: Boolean get() = this == Full

    /** Car Mode and reading surfaces never stack depth; two planes is the readable maximum. */
    fun cap( depth: LumaDepth ): LumaDepth = when ( this ) {
        Minimal -> if ( depth == LumaDepth.Overlay ) LumaDepth.Floating else depth
        else -> depth
    }
}

/**
 * Defaults to [LumaIntensity.Reduced] on purpose: a surface that has not thought about intensity
 * gets the safe middle, never the photographic maximum.
 */
val LocalLumaIntensity = staticCompositionLocalOf { LumaIntensity.Reduced }

@Composable
@ReadOnlyComposable
fun lumaIntensity(): LumaIntensity = LocalLumaIntensity.current

// ---------------------------------------------------------------------------- gloss and bevel

/**
 * The specular highlight.
 *
 * Measured from the references rather than assumed: `images 4.jpg` profiles at luminance
 * 153 / **229** / 79 across top / middle / bottom, so the bright band sits in the *upper middle*
 * and falls off in both directions, over a distinctly darker base. A plain "white at the top fading
 * down" gradient — the usual imitation — misses this and reads as modern glassmorphism instead.
 *
 * The band is kept clear of the lower half so it never runs under a label, which is the theme
 * brief's specific legibility rule for gloss.
 */
fun Modifier.aeroGloss(
    shape: Shape,
    strength: Float = 0.38f,
    enabled: Boolean = true
): Modifier = if ( !enabled || strength <= 0f ) this else this.background(
    brush = Brush.verticalGradient(
        0.00f to Color.White.copy( alpha = strength * 0.55f ),
        0.38f to Color.White.copy( alpha = strength ),
        0.52f to Color.White.copy( alpha = strength * 0.10f ),
        1.00f to Color.Transparent
    ),
    shape = shape
)

/**
 * The bevel: a light edge catching the light and a darker one away from it.
 *
 * This is the cheapest of the four Aero markers and, in the references, the one doing the most work
 * — it is what makes a control read as a physical object rather than a tinted rectangle. Drawn as a
 * single hairline border with a diagonal gradient so it costs one draw call rather than two.
 */
fun Modifier.aeroBevel(
    shape: Shape,
    enabled: Boolean = true
): Modifier = if ( !enabled ) this else this.border(
    width = 1.dp,
    brush = Brush.linearGradient(
        0.00f to Color.White.copy( alpha = 0.55f ),
        0.45f to Color.White.copy( alpha = 0.10f ),
        1.00f to Color.Black.copy( alpha = 0.16f )
    ),
    shape = shape
)

/**
 * Depth, honouring the surface's intensity cap.
 *
 * Uses [Modifier.shadow] rather than a drawn scrim so it composites correctly against a
 * photographic backdrop, which a solid drop rectangle does not.
 */
@Composable
fun Modifier.aeroDepth(
    depth: LumaDepth,
    shape: Shape
): Modifier {
    val capped = lumaIntensity().cap( depth )
    return if ( capped == LumaDepth.Flat ) this
    else this.shadow(
        elevation = capped.elevation,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy( alpha = 0.28f ),
        spotColor = Color.Black.copy( alpha = 0.34f )
    )
}

/**
 * The whole recipe in one call: depth, then fill, then gloss, then bevel — in that order, because
 * the bevel has to sit above the gloss or the edge disappears under the highlight.
 *
 * Components should reach for this rather than assembling their own, which is how an app of this
 * size keeps one gloss recipe instead of drifting into forty.
 */
@Composable
fun Modifier.aeroSurface(
    shape: Shape = RoundedCornerShape( LumaRadius.Panel ),
    fill: Color = LumaColor.Raised,
    depth: LumaDepth = LumaDepth.Resting,
    glossStrength: Float = 0.38f
): Modifier {
    val intensity = lumaIntensity()
    return this
        .aeroDepth( depth, shape )
        .background( fill, shape )
        .aeroGloss( shape, glossStrength, enabled = intensity.allowsGloss )
        .aeroBevel( shape, enabled = intensity.allowsGloss )
}

/**
 * A scrim for text that has to sit over imagery.
 *
 * Fades to [LumaColor.Ground] rather than to black. Text over artwork is drawn in `LumaColor.Ink`,
 * which flips with the skin, so a scrim pinned to black is correct on the dark skins and puts
 * near-black text on a near-black base on the light ones — the exact defect found in the hero and
 * the arch tiles. Verified by `verify.mjs no-fixed-scrims`.
 */
fun Modifier.lumaTextScrim(
    shape: Shape,
    strength: Float = 0.94f
): Modifier = this.background(
    brush = Brush.verticalGradient(
        0.00f to LumaColor.Ground.copy( alpha = 0f ),
        0.42f to LumaColor.Ground.copy( alpha = strength * 0.45f ),
        1.00f to LumaColor.Ground.copy( alpha = strength )
    ),
    shape = shape
)
