package it.fast4x.rimusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.luma.LumaAtmosphere
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaMotion
import app.kreate.android.themed.luma.LumaRingButton
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.enums.PlayerPosition
import it.fast4x.rimusic.ui.components.navigation.header.ActionBar
import it.fast4x.rimusic.utils.transition

/**
 * The shared scaffold — and the single biggest reason the app still looked like the one it was
 * forked from.
 *
 * Eighteen screens are wrapped in this: search results, every library section, album, artist,
 * playlist, history, statistics, moods, settings. Whatever this draws, the user sees almost
 * everywhere, so every one of those screens was still wearing a logo bitmap, a hamburger, a back
 * chevron and a horizontal strip of icon-and-label tabs — regardless of how the *content* beneath
 * it had been redesigned. Redesigning home and the player while leaving this untouched is precisely
 * why the app kept reading as "the original with stuff moved around".
 *
 * ## The tab strip is gone, not restyled
 *
 * The obvious fix — keep the bar, set the labels in the display serif, drop the icons — does not
 * work, and it is worth being explicit about why. **A horizontal strip of section names is still a
 * tab strip.** Retyping it changes every attribute and no shape, which is exactly the class of
 * change that has already been rejected once on this project.
 *
 * So the switcher stops being a bar at all: **the current section is the page's headline.** "Songs"
 * is not a highlighted tab above the list, it is the large serif title of the page you are on, with
 * a chevron. Tapping it opens the other sections as an indented index directly beneath — an
 * editorial contents page, not a control strip — and choosing one collapses it again.
 *
 * What that buys, beyond the silhouette:
 *
 * - The screen gains back the full height of a navigation bar, on every one of the eighteen.
 * - The page finally says what it *is*. The old layout had "Luma" at the top and the section name
 *   in a tab, so the largest text on screen was the app's own name — which the user already knows.
 * - There is one heading instead of a heading *and* a tab bar saying the same word.
 *
 * The chevron is not decoration; a headline that silently hides navigation would be a puzzle. It is
 * the whole discoverability budget for the gesture and it earns its pixels.
 *
 * ## What is deliberately not carried over
 *
 * `NavigationBarPosition` (top / bottom / left / right) and `NavigationBarType` (icon-only vs
 * icon-and-label) no longer do anything here, because there is no bar to position or to style. Those
 * preferences existed to make a component tolerable that has now been deleted. The mini player's
 * top/bottom preference is still honoured — that one describes something that still exists.
 */
@Composable
fun Skeleton(
    navController: NavController,
    tabIndex: Int = 0,
    onTabChanged: (Int) -> Unit = {},
    miniPlayer: @Composable (() -> Unit)? = null,
    navBarContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    // The caller declares its sections by invoking the lambda it is handed, once per section. That
    // contract is kept so all eighteen screens compile untouched; only what is done with the
    // declarations changes. Rebuilt each composition, so it cannot drift out of date.
    val sections = ArrayList<Pair<Int, String>>()
    navBarContent { index, text, _ -> sections.add( index to text ) }

    var expanded by remember { mutableStateOf( false ) }

    // Collapse when the caller switches section by some other route (a back press, a deep link),
    // otherwise the index would sit open showing a selection that has already been made.
    val current = sections.firstOrNull { it.first == tabIndex }?.second
                  ?: sections.firstOrNull()?.second
                  ?: ""

    Box( Modifier.fillMaxSize().background( LumaColor.Ground ) ) {

        // Very low. This is the chrome behind content-heavy screens; anything stronger competes
        // with the artwork the screens themselves are showing.
        LumaAtmosphere( LumaColor.Ember, Modifier.fillMaxSize(), intensity = 0.22f )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Masthead( navController )

            /*
             * A switcher with one option is not a switcher.
             *
             * Album, playlist and artist pages declare a single section — usually called "Songs" —
             * so a headline here would title an album page "Songs" while the album's own name and
             * artwork sat directly beneath it. Those pages already say what they are, and they say
             * it better, with the cover next to it.
             */
            if ( sections.size > 1 )
                SectionHeadline(
                    current = current,
                    sections = sections,
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    onSelect = { index ->
                        expanded = false
                        onTabChanged( index )
                    }
                )

            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = transition(),
                content = content,
                label = "section",
                modifier = Modifier.fillMaxSize()
            )
        }

        val playerPosition by Preferences.MINI_PLAYER_POSITION
        Box(
            Modifier
                .padding( vertical = 5.dp )
                .align(
                    if ( playerPosition == PlayerPosition.Top ) Alignment.TopCenter
                    else Alignment.BottomCenter
                ),
            content = { miniPlayer?.invoke() }
        )
    }
}

/**
 * Back and overflow. No wordmark.
 *
 * The app's own name was previously the largest thing on every screen, which tells the user the one
 * thing they already know. It stays on home, where there is no other subject; everywhere else the
 * subject is the page.
 */
@Composable
private fun Masthead( navController: NavController ) = Row(
    Modifier
        .fillMaxWidth()
        .padding( start = 14.dp, end = 14.dp, top = 8.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    if ( navController.previousBackStackEntry != null )
        LumaRingButton(
            iconRes = R.drawable.chevron_back,
            contentDescription = "Back",
            onClick = navController::navigateUp,
            diameter = 44.dp
        )

    Spacer( Modifier.weight( 1f ) )

    // The overflow keeps its existing contents — appearance, listening, handoff, car mode,
    // settings. It is a long tail of occasional destinations and that is exactly what an overflow
    // is for; rebuilding it was not what made the app look inherited.
    ActionBar( navController )
}

/**
 * The section switcher as a headline.
 *
 * Collapsed it is just the page title. Expanded it lists the alternatives, indented and dimmer, so
 * the open state reads as a contents page rather than a menu that has dropped over the content.
 */
@Composable
private fun SectionHeadline(
    current: String,
    sections: List<Pair<Int, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: ( Int ) -> Unit
) = Column(
    Modifier
        .fillMaxWidth()
        .padding( start = 24.dp, end = 24.dp, top = 2.dp, bottom = 10.dp )
        // So opening the index pushes content down rather than covering it — nothing is hidden
        // behind an overlay the user has to dismiss to read.
        .animateContentSize( animationSpec = LumaMotion.settle() )
) {
    Row(
        Modifier
            .clip( CircleShape )
            .clickable( enabled = sections.size > 1, onClick = onToggle )
            .padding( vertical = 2.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = current,
            style = LumaType.Hero,
            color = LumaColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if ( sections.size > 1 ) {
            Spacer( Modifier.width( 10.dp ) )

            val turn by animateFloatAsState(
                targetValue = if ( expanded ) 180f else 0f,
                animationSpec = LumaMotion.settle(),
                label = "chevron"
            )

            androidx.compose.foundation.Image(
                painter = painterResource( R.drawable.chevron_down ),
                contentDescription = if ( expanded ) "Hide sections" else "Show sections",
                colorFilter = ColorFilter.tint( LumaColor.InkFaint ),
                modifier = Modifier.size( 22.dp ).rotate( turn )
            )
        }
    }

    if ( expanded )
        Column( Modifier.padding( top = 6.dp ) ) {
            sections.filter { it.second != current }
                    .forEach { ( index, label ) ->
                        Text(
                            text = label,
                            style = LumaType.Section,
                            color = LumaColor.InkSoft,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip( CircleShape )
                                .clickable { onSelect( index ) }
                                .padding( vertical = 7.dp )
                        )
                    }
        }
}
