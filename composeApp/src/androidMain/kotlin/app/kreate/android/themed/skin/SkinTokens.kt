package app.kreate.android.themed.skin

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The vocabulary a *skin* is written in.
 *
 * A theme that is only a palette produces ten versions of the same app — the user cannot name why,
 * but they can feel it. Genuine difference needs four independent axes, and this file defines them:
 *
 * - [SkinSurface] — what a surface is made of. Glass behaves differently from paper, which behaves
 *   differently from brushed metal. This is what separates Frutiger Aero from "blue".
 * - [SkinMotion] — personality expressed in time. The same tap can settle like water or stop dead.
 * - [SkinShape] — radius and density.
 * - [SkinOrnament] — what, if anything, lives behind the content.
 *
 * Colour stays in the app's existing `ColorPalette` so every skin recolours the whole app for free;
 * these tokens layer on top of it.
 */

/** How a surface is rendered — the "material" of the skin. */
enum class SkinSurface {

    /** No decoration. Colour alone carries the hierarchy. */
    FLAT,

    /** A top-down specular highlight and a darker foot. The Aero/Vista lozenge. */
    GLOSS,

    /** Translucent, lightly bordered, letting the background through. */
    GLASS,

    /** Matte, faint grain, soft diffuse shadow. Print. */
    PAPER,

    /** Fine directional sheen with a crisp edge. Machined. */
    METAL,

    /** Deep, near-absent surfaces where only type and rules exist. */
    INK
}

/** How a skin behaves in time. */
enum class MotionPersonality {

    /** Overshoots and settles, like water finding level. */
    FLUID,

    /** Moves once, exactly, and stops. No bounce. */
    PRECISE,

    /** Long, soft, unhurried. Nothing snaps. */
    CALM,

    /** Short and immediate. Almost impatient. */
    SNAPPY,

    /** Springy and slightly loose, as if the UI has weight. */
    ORGANIC
}

@Immutable
data class SkinMaterial(
    val surface: SkinSurface,
    /** How strongly a raised surface lightens relative to its base. */
    val elevationTint: Float = 0.04f,
    /** Hairline border width; `0.dp` for skins that use none. */
    val borderWidth: Dp = 0.dp,
    val borderColor: Color = Color.Transparent,
    /** Strength of the specular highlight on [SkinSurface.GLOSS]. */
    val glossStrength: Float = 0f,
    /** Shadow opacity multiplier. Flat skins use 0. */
    val shadowStrength: Float = 0f,
    /** Alpha of translucent surfaces on [SkinSurface.GLASS]. */
    val translucency: Float = 1f
)

@Immutable
data class SkinMotion(
    val personality: MotionPersonality,
    /** Feedback on a press. */
    val fastMs: Int,
    /** Standard transition between states. */
    val mediumMs: Int,
    /** Screen-level or ambient movement. */
    val slowMs: Int,
    val easing: Easing,
    val spring: SpringSpec<Float>
) {
    companion object {

        fun of( personality: MotionPersonality ): SkinMotion = when ( personality ) {

            MotionPersonality.FLUID -> SkinMotion(
                personality, 140, 380, 900,
                CubicBezierEasing( 0.22f, 1f, 0.36f, 1f ),
                spring( dampingRatio = 0.55f, stiffness = Spring.StiffnessLow )
            )

            MotionPersonality.PRECISE -> SkinMotion(
                personality, 90, 200, 420,
                CubicBezierEasing( 0.4f, 0f, 0.2f, 1f ),
                spring( dampingRatio = 1f, stiffness = Spring.StiffnessMedium )
            )

            MotionPersonality.CALM -> SkinMotion(
                personality, 220, 520, 1400,
                LinearOutSlowInEasing,
                spring( dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow )
            )

            MotionPersonality.SNAPPY -> SkinMotion(
                personality, 70, 160, 300,
                FastOutSlowInEasing,
                spring( dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh )
            )

            MotionPersonality.ORGANIC -> SkinMotion(
                personality, 160, 420, 1000,
                CubicBezierEasing( 0.34f, 1.2f, 0.64f, 1f ),
                spring( dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow )
            )
        }
    }
}

@Immutable
data class SkinShape(
    val small: Dp,
    val medium: Dp,
    val large: Dp
) {
    companion object {
        /** Corners so tight the UI reads as engineered rather than friendly. */
        val SHARP = SkinShape( 0.dp, 2.dp, 4.dp )
        val CRISP = SkinShape( 4.dp, 8.dp, 12.dp )
        val SOFT = SkinShape( 10.dp, 16.dp, 24.dp )
        val ROUND = SkinShape( 16.dp, 28.dp, 40.dp )
        /** Almost circular. Everything looks like a pebble. */
        val PEBBLE = SkinShape( 24.dp, 40.dp, 64.dp )
    }
}

/** What lives behind the content, if anything. */
enum class SkinOrnament {

    /** Nothing. The background colour is the background. */
    NONE,

    /** A slow vertical wash between two of the skin's colours. */
    GRADIENT_WASH,

    /** Faint repeating noise, for paper and print skins. */
    GRAIN,

    /** Horizontal scan lines at very low alpha. */
    SCANLINES,

    /** A tessellating geometric motif drawn at low contrast. */
    TESSELLATION,

    /** A photographic sky with drifting bokeh — the Aero treatment. */
    AERO_SKY
}
