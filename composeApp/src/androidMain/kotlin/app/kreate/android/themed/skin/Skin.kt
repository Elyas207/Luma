package app.kreate.android.themed.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.fast4x.rimusic.ui.styling.ColorPalette

/**
 * A complete visual world: colour, material, motion, shape and ornament together.
 *
 * Each skin is meant to feel like a different product rather than a recolour. Where two skins share
 * a hue they differ in how their surfaces are made and how they move, which is the part the eye
 * reads as "a different app" even when it cannot articulate why.
 */
@Immutable
data class Skin(
    val id: SkinId,
    val displayName: String,
    /** One line, shown under the name in the picker. Sets the mood before anything is applied. */
    val tagline: String,
    val palette: ColorPalette,
    val material: SkinMaterial,
    val motion: SkinMotion,
    val shape: SkinShape,
    val ornament: SkinOrnament
)

enum class SkinId {
    AURORA, OBSIDIAN, EMBER, VINYL, CASSETTE,
    TERRAZZO, NOCTURNE, BLOOM, GRAPHITE, ZELLIGE
}

private fun palette(
    bg0: Long, bg1: Long, bg2: Long, bg3: Long, bg4: Long,
    accent: Long, onAccent: Long,
    text: Long, textSecondary: Long, textDisabled: Long,
    isDark: Boolean
) = ColorPalette(
    background0 = Color( bg0 ),
    background1 = Color( bg1 ),
    background2 = Color( bg2 ),
    background3 = Color( bg3 ),
    background4 = Color( bg4 ),
    accent = Color( accent ),
    onAccent = Color( onAccent ),
    text = Color( text ),
    textSecondary = Color( textSecondary ),
    textDisabled = Color( textDisabled ),
    isDark = isDark,
    iconButtonPlayer = Color( accent )
)

object Skins {

    /**
     * Frutiger Aero. Bright sky, water, glass and optimism.
     *
     * The era's look was never really "blue gradients" — it was *optimism about technology*
     * expressed through nature: water, glass, sky, and a sense that the machine was friendly. So
     * this skin is built from a photographic sky, glossy lozenges with a real specular highlight,
     * and motion that overshoots and settles the way water does. The bright aqua reads as
     * confident rather than cold because the greens keep it organic.
     */
    val AURORA = Skin(
        id = SkinId.AURORA,
        displayName = "Aurora",
        tagline = "Sky, water and glass. Unreasonably optimistic.",
        // Light, not dark. The authentic article was *bright* — Vista and 7 were sunlit, and a
        // cautious navy version would be a different theme wearing its name. With ten skins
        // available, this one is free to commit; anyone wanting a dark surface has nine others.
        palette = palette(
            bg0 = 0xFFDCEEF8, bg1 = 0xFFE8F5FC, bg2 = 0xFFF4FAFE,
            bg3 = 0xFFFFFFFF, bg4 = 0xFFC5E2F2,
            accent = 0xFF12A5C8, onAccent = 0xFFFFFFFF,
            text = 0xFF0B2E42, textSecondary = 0xFF35607A, textDisabled = 0xFF8FB3C7,
            isDark = false
        ),
        material = SkinMaterial(
            surface = SkinSurface.GLOSS,
            elevationTint = 0.10f,
            borderWidth = 1.dp,
            borderColor = Color( 0x55FFFFFF ),
            glossStrength = 0.32f,
            shadowStrength = 0.5f,
            translucency = 0.82f
        ),
        motion = SkinMotion.of( MotionPersonality.FLUID ),
        shape = SkinShape.ROUND,
        ornament = SkinOrnament.AERO_SKY
    )

    /**
     * Pure black, hairline rules, no ornament at all. For OLED and for people who want the
     * interface to disappear behind the artwork.
     */
    val OBSIDIAN = Skin(
        id = SkinId.OBSIDIAN,
        displayName = "Obsidian",
        tagline = "Absolute black. Nothing you didn't ask for.",
        palette = palette(
            bg0 = 0xFF000000, bg1 = 0xFF070707, bg2 = 0xFF101010,
            bg3 = 0xFF181818, bg4 = 0xFF222222,
            accent = 0xFFEDEDED, onAccent = 0xFF000000,
            text = 0xFFFFFFFF, textSecondary = 0xFF8A8A8A, textDisabled = 0xFF4A4A4A,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.INK,
            elevationTint = 0.02f,
            borderWidth = 1.dp,
            borderColor = Color( 0x1AFFFFFF ),
            shadowStrength = 0f
        ),
        motion = SkinMotion.of( MotionPersonality.PRECISE ),
        shape = SkinShape.SHARP,
        ornament = SkinOrnament.NONE
    )

    /** Lamplight and warmth. Slow, soft, for the end of the day. */
    val EMBER = Skin(
        id = SkinId.EMBER,
        displayName = "Ember",
        tagline = "Lamplight. Slow evenings.",
        palette = palette(
            bg0 = 0xFF1A1210, bg1 = 0xFF241815, bg2 = 0xFF32211C,
            bg3 = 0xFF412B23, bg4 = 0xFF54372C,
            accent = 0xFFFF9E5E, onAccent = 0xFF2B1206,
            text = 0xFFFBEFE6, textSecondary = 0xFFC9A793, textDisabled = 0xFF8A6E5F,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.PAPER,
            elevationTint = 0.05f,
            shadowStrength = 0.35f
        ),
        motion = SkinMotion.of( MotionPersonality.CALM ),
        shape = SkinShape.SOFT,
        ornament = SkinOrnament.GRAIN
    )

    /** Analogue warmth: cream, burnt orange, and circles everywhere. */
    val VINYL = Skin(
        id = SkinId.VINYL,
        displayName = "Vinyl",
        tagline = "Warm, analogue, a little worn.",
        palette = palette(
            bg0 = 0xFFF3E9D8, bg1 = 0xFFEADFC9, bg2 = 0xFFE0D2B8,
            bg3 = 0xFFD3C1A2, bg4 = 0xFFC2AC89,
            accent = 0xFFB4491F, onAccent = 0xFFFFF4E8,
            text = 0xFF2A1F16, textSecondary = 0xFF5E4C3B, textDisabled = 0xFF9C8A76,
            isDark = false
        ),
        material = SkinMaterial(
            surface = SkinSurface.PAPER,
            elevationTint = 0.03f,
            borderWidth = 1.dp,
            borderColor = Color( 0x22000000 ),
            shadowStrength = 0.25f
        ),
        motion = SkinMotion.of( MotionPersonality.ORGANIC ),
        shape = SkinShape.PEBBLE,
        ornament = SkinOrnament.GRAIN
    )

    /** Magnetic tape and CRT glow, restrained enough to still be usable at night. */
    val CASSETTE = Skin(
        id = SkinId.CASSETTE,
        displayName = "Cassette",
        tagline = "Tape hiss and CRT glow.",
        palette = palette(
            bg0 = 0xFF120B24, bg1 = 0xFF1B1036, bg2 = 0xFF261748,
            bg3 = 0xFF33205E, bg4 = 0xFF442C79,
            accent = 0xFFFF4D9D, onAccent = 0xFF1A0010,
            text = 0xFFF0E9FF, textSecondary = 0xFF9F8BD4, textDisabled = 0xFF6A5A96,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.GLASS,
            elevationTint = 0.08f,
            borderWidth = 1.dp,
            borderColor = Color( 0x4400E5FF ),
            shadowStrength = 0.4f,
            translucency = 0.88f
        ),
        motion = SkinMotion.of( MotionPersonality.SNAPPY ),
        shape = SkinShape.CRISP,
        ornament = SkinOrnament.SCANLINES
    )

    /** Gallery print: off-white, speckled, generous margins, quiet. */
    val TERRAZZO = Skin(
        id = SkinId.TERRAZZO,
        displayName = "Terrazzo",
        tagline = "Off-white, printed, quiet.",
        palette = palette(
            bg0 = 0xFFFAF8F3, bg1 = 0xFFF2EFE7, bg2 = 0xFFE8E4D9,
            bg3 = 0xFFDBD5C7, bg4 = 0xFFC9C2B0,
            accent = 0xFF2F5D50, onAccent = 0xFFF7FBF9,
            text = 0xFF1C1C1A, textSecondary = 0xFF57554F, textDisabled = 0xFF9A978E,
            isDark = false
        ),
        material = SkinMaterial(
            surface = SkinSurface.PAPER,
            elevationTint = 0.02f,
            borderWidth = 1.dp,
            borderColor = Color( 0x14000000 ),
            shadowStrength = 0.15f
        ),
        motion = SkinMotion.of( MotionPersonality.CALM ),
        shape = SkinShape.CRISP,
        ornament = SkinOrnament.GRAIN
    )

    /** Widescreen and dim. Letterboxed, slow dissolves, everything deferring to the artwork. */
    val NOCTURNE = Skin(
        id = SkinId.NOCTURNE,
        displayName = "Nocturne",
        tagline = "Widescreen and dimmed. Let it play.",
        palette = palette(
            bg0 = 0xFF0A0D14, bg1 = 0xFF11151F, bg2 = 0xFF1A1F2C,
            bg3 = 0xFF242A3A, bg4 = 0xFF31384B,
            accent = 0xFFD8C08A, onAccent = 0xFF14100A,
            text = 0xFFEDEFF5, textSecondary = 0xFF98A0B4, textDisabled = 0xFF5D657A,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.FLAT,
            elevationTint = 0.05f,
            shadowStrength = 0.6f
        ),
        motion = SkinMotion.of( MotionPersonality.CALM ),
        shape = SkinShape.SHARP,
        ornament = SkinOrnament.GRADIENT_WASH
    )

    /** Soft, rounded and friendly without being childish. */
    val BLOOM = Skin(
        id = SkinId.BLOOM,
        displayName = "Bloom",
        tagline = "Soft edges and good moods.",
        palette = palette(
            bg0 = 0xFFFFF7FB, bg1 = 0xFFFDEDF5, bg2 = 0xFFF8DEEC,
            bg3 = 0xFFEFCADE, bg4 = 0xFFE2B0CB,
            accent = 0xFF7C5CFF, onAccent = 0xFFFFFFFF,
            text = 0xFF241B2E, textSecondary = 0xFF5E526B, textDisabled = 0xFFA79BB2,
            isDark = false
        ),
        material = SkinMaterial(
            surface = SkinSurface.FLAT,
            elevationTint = 0.04f,
            shadowStrength = 0.2f
        ),
        motion = SkinMotion.of( MotionPersonality.ORGANIC ),
        shape = SkinShape.PEBBLE,
        ornament = SkinOrnament.NONE
    )

    /** An instrument, not a toy. Grid, hairlines, dense information, no decoration. */
    val GRAPHITE = Skin(
        id = SkinId.GRAPHITE,
        displayName = "Graphite",
        tagline = "An instrument. Reads like a spec sheet.",
        palette = palette(
            bg0 = 0xFF141618, bg1 = 0xFF1B1E21, bg2 = 0xFF23272B,
            bg3 = 0xFF2D3237, bg4 = 0xFF3A4046,
            accent = 0xFF5AC8FA, onAccent = 0xFF04161D,
            text = 0xFFE7EBEE, textSecondary = 0xFF97A0A8, textDisabled = 0xFF636C74,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.METAL,
            elevationTint = 0.06f,
            borderWidth = 1.dp,
            borderColor = Color( 0x22FFFFFF ),
            shadowStrength = 0.2f
        ),
        motion = SkinMotion.of( MotionPersonality.PRECISE ),
        shape = SkinShape.SHARP,
        ornament = SkinOrnament.NONE
    )

    /**
     * Deep teal and brass with a tessellating geometric motif.
     *
     * Included because this library is heavily recitation and nasheed, and none of the other nine
     * skins offer a visual language that suits that listening. The motif is drawn at very low
     * contrast as background structure — decoration, never imagery.
     */
    val ZELLIGE = Skin(
        id = SkinId.ZELLIGE,
        displayName = "Zellige",
        tagline = "Deep teal, brass, and geometry.",
        palette = palette(
            bg0 = 0xFF08201F, bg1 = 0xFF0C2C2B, bg2 = 0xFF113C39,
            bg3 = 0xFF174E4A, bg4 = 0xFF1F645E,
            accent = 0xFFCBA653, onAccent = 0xFF1A1204,
            text = 0xFFF3F7F4, textSecondary = 0xFFA7C4BC, textDisabled = 0xFF6A8A83,
            isDark = true
        ),
        material = SkinMaterial(
            surface = SkinSurface.FLAT,
            elevationTint = 0.06f,
            borderWidth = 1.dp,
            borderColor = Color( 0x33CBA653 ),
            shadowStrength = 0.3f
        ),
        motion = SkinMotion.of( MotionPersonality.CALM ),
        shape = SkinShape.CRISP,
        ornament = SkinOrnament.TESSELLATION
    )

    val ALL = listOf(
        AURORA, OBSIDIAN, EMBER, VINYL, CASSETTE,
        TERRAZZO, NOCTURNE, BLOOM, GRAPHITE, ZELLIGE
    )

    fun byId( id: SkinId ): Skin = ALL.first { it.id == id }

    fun byIdOrNull( name: String? ): Skin? =
        name?.let { n -> ALL.firstOrNull { it.id.name == n } }
}
