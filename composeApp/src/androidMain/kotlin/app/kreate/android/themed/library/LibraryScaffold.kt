package app.kreate.android.themed.library

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.utils.transition
import androidx.compose.foundation.Image

/**
 * The library's own scaffold — one line of chrome instead of four.
 *
 * What this replaces was stacked like a filing cabinet: an app bar (logo, search, menu), then an
 * `<h1>` naming the section, then a row of icon tools, then a row of filter chips, then a five-tab
 * bar pinned to the bottom. Close to a quarter of the display was spent before a single song, and
 * the word "Songs" appeared three times in it — once in the heading, once in the chips, once in the
 * tab bar. Three labels for one idea is not navigation, it is furniture.
 *
 * The first attempt at fixing that replaced the tab bar with a horizontal rail of section names.
 * That was not enough, and the reason is worth keeping: **a horizontal strip of section names is
 * still a tab strip.** It changed the type, the colour and the container and left the silhouette
 * exactly where it was.
 *
 * So the rail is gone too. The current section is the page's headline and the others fold into it —
 * see [app.kreate.android.themed.luma.LumaSectionHeadline], which
 * [it.fast4x.rimusic.ui.components.Skeleton] also uses, so the two scaffolds can no longer drift
 * into two different answers to the same question.
 */
@Composable
fun LibraryScaffold(
    navController: NavController,
    sections: List<Pair<Int, Int>>,
    tabIndex: Int,
    onTabChanged: ( Int ) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    content: @Composable ( Int ) -> Unit
) = Box(
    Modifier
        .fillMaxSize()
        .background( LumaColor.Ground )
        .statusBarsPadding()
) {
    val expandedState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf( false ) }
    val expanded = expandedState.value

    val labelled = sections.map { ( index, labelId ) ->
        index to androidx.compose.ui.res.stringResource( labelId )
    }
    val current = labelled.firstOrNull { it.first == tabIndex }?.second
                  ?: labelled.firstOrNull()?.second
                  ?: ""

    Column( Modifier.fillMaxSize() ) {

        Row(
            Modifier
                .fillMaxWidth()
                .padding( start = 8.dp, end = 12.dp, top = 8.dp ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphButton( R.drawable.chevron_back, "Back" ) { navController.navigateUp() }

            androidx.compose.foundation.layout.Spacer( Modifier.weight( 1f ) )

            GlyphButton( R.drawable.settings, "Settings" ) {
                NavRoutes.settings.navigateHere( navController )
            }
        }

        app.kreate.android.themed.luma.LumaSectionHeadline(
            current = current,
            sections = labelled,
            expanded = expanded,
            onToggle = { expandedState.value = !expandedState.value },
            onSelect = { index ->
                expandedState.value = false
                onTabChanged( index )
            }
        )

        AnimatedContent(
            targetState = tabIndex,
            transitionSpec = transition(),
            label = "library-section",
            modifier = Modifier.fillMaxSize()
        ) { content( it ) }
    }

    Box(
        Modifier
            .padding( vertical = 5.dp )
            .align( Alignment.BottomCenter ),
        content = { miniPlayer() }
    )
}

@Composable
private fun GlyphButton( iconId: Int, description: String, onClick: () -> Unit ) = Box(
    Modifier
        .size( 40.dp )
        .clip( CircleShape )
        .clickable( onClick = onClick ),
    contentAlignment = Alignment.Center
) {
    Image(
        painter = painterResource( iconId ),
        contentDescription = description,
        colorFilter = ColorFilter.tint( LumaColor.InkSoft ),
        modifier = Modifier.size( 20.dp )
    )
}
