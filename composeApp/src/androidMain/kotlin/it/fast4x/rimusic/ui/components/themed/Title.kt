package it.fast4x.rimusic.ui.components.themed

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType

/**
 * The section headings, used on roughly forty screens.
 *
 * All four of these were the same thing in different sizes: bold sans, `LumaColor.Ink`, an
 * arrow on the right. That is the default heading of every Android app ever built, and because it
 * appears on nearly every screen it did more to make Luma look inherited than any individual layout
 * did.
 *
 * Now they are the display serif, and they are ranked by *size and colour* rather than by weight —
 * the serif ships in one weight, so bolding it would only produce a synthesised smear.
 * [TitleMiniSection] drops out of the serif entirely and becomes wide-tracked micro caps, which is
 * the counterweight that makes the serif read as editorial rather than merely decorative.
 */
@Composable
fun Title(
    title: String,
    modifier: Modifier = Modifier,
    verticalPadding: Dp = 12.dp,
    @DrawableRes icon: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) {
                if (enableClick)
                    onClick?.invoke()
            }
            .padding(horizontal = 22.dp, vertical = verticalPadding)
    ) {
        Text(
            text = title,
            style = LumaType.Section,
            color = LumaColor.Ink,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )

        if (onClick != null && enableClick) {
            Icon(
                painter = painterResource(icon ?: R.drawable.arrow_forward),
                contentDescription = null,
                // The affordance should be findable, not loud. At full ink it competed with the
                // heading it belongs to.
                tint = LumaColor.InkFaint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun Title2Actions(
    title: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon1: Int? = R.drawable.arrow_forward,
    @DrawableRes icon2: Int? = R.drawable.arrow_forward,
    enableClick: Boolean = true,
    onClick1: (() -> Unit)? = null,
    onClick2: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .clickable(enabled = onClick1 != null) {
                if (enableClick)
                    onClick1?.invoke()
            }
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = LumaType.Section,
            color = LumaColor.Ink,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )

        if (onClick2 != null && enableClick) {
            Icon(
                painter = painterResource(icon2 ?: R.drawable.arrow_forward),
                contentDescription = null,
                tint = LumaColor.InkFaint,
                modifier = Modifier
                    .clickable { onClick2.invoke() }
                    .padding(end = 14.dp)
                    .size(20.dp)
            )
        }

        if (onClick1 != null && enableClick) {
            Icon(
                painter = painterResource(icon1 ?: R.drawable.arrow_forward),
                contentDescription = null,
                tint = LumaColor.InkFaint,
                modifier = Modifier
                    .clickable { onClick1.invoke() }
                    .size(20.dp)
            )
        }
    }
}

/** A heading with nothing to its right — the largest of the four. */
@Composable
fun TitleSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = LumaType.Title,
        color = LumaColor.Ink,
        textAlign = TextAlign.Start,
        modifier = modifier.padding(end = 12.dp)
    )
}

/**
 * The smallest rank, and the only one that is not serif.
 *
 * Small bold sans and small serif are hard to tell apart at a glance, so a fourth *size* of the same
 * treatment would have added a rank the eye cannot actually resolve. Wide-tracked caps are
 * unmistakably a different kind of label, which is what this rank is for.
 */
@Composable
fun TitleMiniSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        style = LumaType.Label,
        color = LumaColor.InkFaint,
        textAlign = TextAlign.Start,
        modifier = modifier.padding(top = 8.dp)
    )
}
