package app.kreate.android.themed.rimusic.component.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kreate.android.themed.common.component.AbstractSearch
import app.kreate.android.themed.common.component.settings.SettingEntrySearch

@Composable
private fun SettingEntrySearch.AnimatedHeader() {
    AnimatedContent(
        targetState = isVisible,
        transitionSpec = {
            if ( targetState ) {
                slideInVertically { height -> height } + expandHorizontally() + fadeIn() togetherWith
                        slideOutVertically { height -> -height } + shrinkHorizontally() + fadeOut()
            } else {
                slideInVertically { height -> -height } + expandHorizontally() + fadeIn() togetherWith
                        slideOutVertically { height -> height } + shrinkHorizontally() + fadeOut()
            }.using(
                // Disable clipping since the faded slide-in/out should
                // be displayed out of bounds.
                SizeTransform(clip = false)
            )
        },
        label = "animated header and search bar"
    ) { target ->
        if (target)
            SearchBar()
        else
            // Nothing. The page's name is the scaffold's headline; repeating it here was the
            // duplication this header existed to create.
            androidx.compose.foundation.layout.Spacer( Modifier.fillMaxWidth() )
    }
}

/**
 * The settings page's own header.
 *
 * It used to draw an app-icon plus the page name, centred — "General" with a badge, directly under
 * the scaffold's headline, which by then also said "General". Two titles for one page, one of them
 * decorated with an icon of the app you are already inside.
 *
 * The *search* it carries is genuinely useful — a settings page this long is unusable without one —
 * so only the duplicated title goes. Tapping the magnifier still opens the field, which now has the
 * row to itself and is aligned with the rest of the page instead of floating in the middle of it.
 */
@Composable
fun SettingEntrySearch.RiMusicAnimatedHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
                           .height( 40.dp )
                           .padding( horizontal = 22.dp )
    ) {
        // Field first so it can grow, control last so it lands on the right where the other
        // header controls on this screen already are.
        androidx.compose.foundation.layout.Box( Modifier.weight( 1f ) ) { AnimatedHeader() }

        HeaderIcon(
            Modifier.padding( start = AbstractSearch.DECO_BOX_ITEM_SPACING.dp )
                    .align( Alignment.CenterVertically )
        )
    }
}