package app.kreate.android.themed.luma

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kreate.android.R

/**
 * Luma's design language — "emanation".
 *
 * The mark is a bright point with arcs radiating from it, and that is also the organising idea of
 * the interface: **every screen has one circular focus, and everything else falls away from it.**
 *
 * This matters because it is the one thing a squint test catches. Every media app on the market —
 * Spotify, Apple Music, Deezer, YouTube Music — draws the same silhouette: a grid of rounded
 * rectangles under a bold sans heading. Comparing their home screens side by side, they are
 * genuinely hard to tell apart. Restyling that grid produces a differently-coloured version of the
 * same app; the shapes on screen have to change or nothing has changed at all.
 *
 * So Luma commits to three things no shelf-and-card app does:
 *
 * 1. **Circles carry the focus.** Artwork on the player is a disc, progress is the arc around it,
 *    and the transport is a ring rather than a filled pill. Nothing else in the category does this,
 *    and it is legible at a glance from across a car.
 * 2. **The arch is the tile.** A semicircular top on a squared base — see [LumaShape.Arch]. It
 *    reads as a window rather than a card, and it is the rectangle's opposite at the same cost.
 * 3. **Type is the texture.** Oversized display serif set tight, with artwork allowed to overlap
 *    it, instead of a small bold sans label sitting obediently above a row.
 *
 * The layer is deliberately **additive**. It does not touch [it.fast4x.rimusic.ui.styling.Typography]
 * or `colorPalette()`, which between them are referenced by most of the 700-odd files inherited from
 * four previous code generations. Screens opt in by using these tokens; nothing breaks by ignoring
 * them.
 */
object LumaType {

    /**
     * Instrument Serif, used only at display sizes.
     *
     * A high-contrast editorial serif rather than another geometric sans, because the sans is where
     * every music app already lives and the difference between Poppins and Rubik is not a difference
     * anyone perceives. It ships in one weight on purpose — a display face with five weights invites
     * a hierarchy built out of boldness, which is exactly the generic look being avoided here.
     * Hierarchy comes from *size and space* instead.
     *
     * SIL Open Font License; the text ships in `assets/fonts/`.
     */
    val Display = FontFamily(
        Font( R.font.instrument_serif_regular, FontWeight.Normal ),
        Font( R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic )
    )

    /**
     * Serifs at display size need their line box trimmed, or the ascent/descent metrics baked into
     * the font leave a visible gap above every heading that no amount of padding explains.
     */
    private val trim = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both
    )

    /*
     * Every style carries a colour, and that is not optional.
     *
     * The scale these replaced came out of `typographyOf(color = …)`, so a `TextStyle` here always
     * arrived with ink already in it — and a great deal of code relies on that, passing only
     * `style =` and never `color =`. `BasicText` in particular falls back to **black** for an
     * unspecified colour, so a style without one renders invisibly on this app's near-black ground
     * rather than merely looking wrong. Leaving the colour out is a silent, screen-by-screen
     * failure; putting it in costs nothing, and any caller that wants a different one still says
     * `.copy(colour)` as before.
     */
    /*
     * Built on read, not once at class-init.
     *
     * These carry a colour (see below), and an object's `val` is evaluated the first time the
     * object is touched — which would freeze whatever skin happened to be active at that moment and
     * leave every heading in the app the wrong colour after a theme change. A `get()` costs one
     * small allocation per read and is always correct.
     */
    private val display get() = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Normal,
        color = LumaColor.Ink,
        lineHeightStyle = trim,
        platformStyle = PlatformTextStyle( includeFontPadding = false )
    )

    /** The one thing the screen is about. Set tight — display serifs want negative tracking. */
    val Hero get() = display.copy( fontSize = 54.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp )

    /** A now-playing title, a screen name. */
    val Title get() = display.copy( fontSize = 38.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp )

    /** Section headings, when they earn a heading at all. */
    val Section get() = display.copy( fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-0.4).sp )

    /** Track titles in a row — serif at body size still reads as Luma and not as a system list. */
    val Row get() = display.copy( fontSize = 20.sp, lineHeight = 23.sp, letterSpacing = (-0.2).sp )

    /**
     * Captions under a tile.
     *
     * The smallest size the serif is allowed to appear at. Below roughly this, its high stroke
     * contrast starts to break up and it reads as a rendering fault rather than as a typeface —
     * anything smaller uses [Meta] or [Label] instead.
     */
    val Tile get() = display.copy( fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp )

    /**
     * Wide-tracked micro caps.
     *
     * The counterweight that makes the serif read as editorial rather than merely decorative: a very
     * large elegant serif beside very small wide-set caps is the oldest premium-print signature
     * there is, and it costs nothing. Deliberately kept above 10sp so it stays legible.
     */
    val Label get() = TextStyle(
        fontWeight = FontWeight.Medium,
        color = LumaColor.InkFaint,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 2.2.sp,
        platformStyle = PlatformTextStyle( includeFontPadding = false )
    )

    /**
     * Durations, counts, positions.
     *
     * [Label]'s 2.2sp tracking is right for two or three words of caps and wrong for digits — a
     * timestamp set that loose stops reading as one number and starts reading as separate figures.
     * Same family and same restraint, a third of the tracking.
     */
    val Numeral get() = TextStyle(
        fontWeight = FontWeight.Normal,
        color = LumaColor.InkFaint,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.7.sp,
        platformStyle = PlatformTextStyle( includeFontPadding = false )
    )

    /** The same idea one step up, for things that are read rather than scanned. */
    val Meta get() = TextStyle(
        fontWeight = FontWeight.Normal,
        color = LumaColor.InkSoft,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.2.sp,
        platformStyle = PlatformTextStyle( includeFontPadding = false )
    )
}

object LumaShape {

    /** The focus. */
    val Disc = CircleShape

    /**
     * The arch — a semicircular top on a squared base.
     *
     * Percent-based top corners so the curve stays a true half-circle at any tile width; a fixed dp
     * radius would flatten into a rounded rectangle as the tile grows, which is the shape being
     * avoided. The base keeps a small radius so it sits on a surface rather than being stamped into
     * it.
     */
    val Arch = RoundedCornerShape(
        topStartPercent = 50,
        topEndPercent = 50,
        bottomStartPercent = 6,
        bottomEndPercent = 6
    )

    /** For artwork that must stay recognisable — an album sleeve cropped to an arch is a bad trade. */
    val Sleeve = RoundedCornerShape( 20.dp )

    /** Rows, sheets, and anything the eye should pass over. */
    val Soft = RoundedCornerShape( 16.dp )

    val Pill = CircleShape
}

object LumaMotion {

    /**
     * Everything lands with a small overshoot.
     *
     * Motion is the axis of a design system most often left at the default, and a default easing
     * curve is the single most recognisable "this was assembled from a component library" tell.
     * A spring that settles just past its target reads as physical — the interface has mass — and it
     * is one line per animation.
     */
    fun <T> settle() = spring<T>(
        dampingRatio = 0.62f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** For things that should not bounce: scrims, crossfades, colour. */
    fun <T> fade( durationMillis: Int = 420 ) = tween<T>(
        durationMillis = durationMillis,
        easing = CubicBezierEasing( 0.2f, 0f, 0f, 1f )
    )

    /** The slow swell under a playing track. Long enough not to nag. */
    const val BREATH_MILLIS = 4200
}

/**
 * Luma's colour roles, resolved from whatever skin is active.
 *
 * ## What went wrong here, and why it is worth writing down
 *
 * These began as fixed constants — a single warm near-black identity — and were then swept across
 * ~200 call sites in place of `colorPalette()`. That silently deleted the entire skin system: ten
 * themes still existed in settings and none of them changed a pixel, because every screen had been
 * hardcoded to one palette. Committing the app to one look was a design opinion; taking a working
 * user-facing feature away to get it was not, and it should never have been done as a side effect
 * of a find-and-replace.
 *
 * ## How it works now
 *
 * The roles are backed by Compose state that [SyncLumaPalette] keeps pointed at the active
 * `ColorPalette`. Reading a role inside a composable subscribes to that state, so switching skin
 * recomposes every screen automatically. Reading one outside composition (a `Values.from(...)`
 * factory, a `DataSource`) still gets the current value rather than a stale constant.
 *
 * This keeps the identity where it actually lives. Luma is recognisable because of its *shapes* —
 * the disc, the arch, the ring, the display serif — and none of that is a colour. A skin can repaint
 * the surface without touching any of it, which is exactly the separation `DESIGN.md` argues for.
 */
@Immutable
object LumaColor {

    /**
     * The palette every role reads through.
     *
     * `mutableStateOf` rather than a plain field on purpose: it is what makes a skin change
     * propagate: composables that read a role are subscribed to it and recompose on write.
     */
    private var palette by mutableStateOf( DefaultDarkColorPalette )

    internal fun sync( active: ColorPalette ) {
        if ( active != palette ) palette = active
    }

    /** The page. */
    val Ground: Color get() = palette.background0

    /** Anything sitting on the page — sheets, menus, the mini player, artwork placeholders. */
    val Raised: Color get() = palette.background2

    /** A divider that should be felt rather than seen. */
    val Hairline: Color
        get() = ( if ( palette.isDark ) Color.White else Color.Black ).copy( alpha = 0.08f )

    /** Primary reading colour. */
    val Ink: Color get() = palette.text

    /** Supporting text — artists, descriptions, the second line of a row. */
    val InkSoft: Color get() = palette.textSecondary

    /** Reference detail: durations, counts, micro-labels, inactive affordances. */
    val InkFaint: Color get() = palette.textDisabled

    /**
     * The one thing on a screen that is allowed to be a colour.
     *
     * Named for what it does rather than for a hue, because under a light skin it is not an ember
     * at all — it is whatever that skin decided its accent should be.
     */
    val Ember: Color get() = palette.accent

    /** Text or glyphs drawn *on* [Ember]. */
    val OnEmber: Color get() = palette.onAccent

    /** Errors and destructive actions. */
    val Alarm: Color get() = palette.red
}

/**
 * Keeps [LumaColor] pointed at the active skin.
 *
 * Installed once, high in the tree, next to where `LocalAppearance` is provided. Without it every
 * Luma surface would render against the default dark palette no matter which skin is selected —
 * which is precisely the bug this file exists to have fixed.
 */
@Composable
fun SyncLumaPalette( palette: ColorPalette ) {
    androidx.compose.runtime.SideEffect { LumaColor.sync( palette ) }
}
