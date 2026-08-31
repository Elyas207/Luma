package it.fast4x.rimusic.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.screen.home.HomeSongsScreen
import it.fast4x.compose.persist.PersistMapCleanup
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.toUiMood
import app.kreate.android.themed.library.LibraryScaffold


@ExperimentalMaterial3Api
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistUrl: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {}
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    PersistMapCleanup("home/")

    val (tabIndex, onTabChanged) = Preferences.HOME_TAB_INDEX

    // The rail replaces both the five-icon tab bar and the per-section `<h1>`; see [LibraryScaffold]
    // for why the two were saying the same thing twice.
    val sections = buildList {
        if ( Preferences.QUICK_PICKS_PAGE.value )
            add( 0 to R.string.quick_picks )
        add( 1 to R.string.songs )
        add( 2 to R.string.artists )
        add( 3 to R.string.albums )
        add( 4 to R.string.playlists )
    }

    LibraryScaffold(
        navController = navController,
        sections = sections,
        tabIndex = tabIndex,
        onTabChanged = onTabChanged,
        miniPlayer = miniPlayer
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            when (currentTabIndex) {
                0 -> HomeQuickPicks(
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onMoodClick = { mood ->
                        navController.currentBackStackEntry?.savedStateHandle?.set("mood", mood.toUiMood())
                        NavRoutes.mood.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    },
                    navController = navController

                )

                1 -> HomeSongsScreen( navController )

                2 -> HomeArtists(
                    navController = navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                3 -> HomeAlbums(
                    navController = navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }
                )

                4 -> HomeLibrary(
                    navController,
                    onSearchClick = {
                        NavRoutes.search.navigateHere( navController )
                    },
                    onSettingsClick = {
                        NavRoutes.settings.navigateHere( navController )
                    }

                )
            }
        }
    }
}
