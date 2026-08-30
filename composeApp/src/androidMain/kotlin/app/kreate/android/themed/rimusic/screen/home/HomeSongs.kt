package app.kreate.android.themed.rimusic.screen.home

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.EmptyState
import it.fast4x.rimusic.enums.NavRoutes
import app.kreate.android.constant.MenuPage
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.common.component.BottomMenu
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.song.PeriodSelector
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.utils.shallowCompare
import app.kreate.constant.SongSortBy
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.util.toDuration
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.HiddenSongs
import org.koin.compose.koinInject

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun HomeSongs(
    navController: NavController,
    builtInPlaylist: BuiltInPlaylist,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: MutableList<Song>,
    getSongs: () -> List<Song>,
    menu: BottomMenu = LocalBottomMenu.current
) {
    // Essentials
    val player: StatefulPlayer = koinInject()
    val cache: Cache = koinInject(CacheType.CACHE)
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val (colorPalette, typography) = LocalAppearance.current

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by Preferences.PARENTAL_CONTROL
    val maxTopPlaylistItems by Preferences.MAX_NUMBER_OF_TOP_PLAYED
    val includeLocalSongs by Preferences.HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL
    val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION
    //</editor-fold>

    var items by persistList<Song>( "home/songs" )

    val songSort = remember {
        Sort(menuState, Preferences.HOME_SONGS_SORT_BY, Preferences.HOME_SONGS_SORT_ORDER)
    }
    val topPlaylists = remember { PeriodSelector(menuState) }
    val hiddenSongs = HiddenSongs()
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = builtInPlaylist.text,
        songs = getSongs
    )
    val downloadAllDialog = remember {
        DownloadAllDialog( context, getSongs )
    }
    val deleteDownloadsDialog = remember {
        DeleteAllDownloadedDialog(getSongs)
    }

    /**
     * This variable tells [LazyColumn] to render [SongItemPlaceholder]
     * instead of [SongItem] queried from the database.
     *
     * This indication also tells user that songs are being loaded
     * and not it's definitely not freezing up.
     *
     * > This variable should **_NOT_** be set to `false` while inside **first** phrase,
     * and should **_NOT_** be set to `true` while in **second** phrase.
     */
    // Starts *true*: the very first composition happens before the query has been asked, and a
    // false start let the empty state ("No songs yet") render over a full library for a frame.
    var isLoading by rememberSaveable { mutableStateOf(true) }

    // This phrase loads all songs across types into [items]
    // No filtration applied to this stage, only sort
    LaunchedEffect( builtInPlaylist, topPlaylists.period, songSort.sortBy, songSort.sortOrder, hiddenSongs.isFirstIcon ) {
        isLoading = true

        val retrievedSongs = when( builtInPlaylist ) {
            BuiltInPlaylist.All -> Database.songTable
                                           .sortAll( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                                           .map { list ->
                                               // Include local songs if enabled
                                               list.fastFilter {
                                                   !includeLocalSongs || !it.isLocal
                                               }
                                           }

            BuiltInPlaylist.Downloaded -> {
                // [MyDownloadHelper] provide a list of downloaded songs, which is faster to retrieve
                // than using `Cache.isCached()` call
                val downloaded: List<String> = MyDownloadHelper.instance
                                                               .downloads
                                                               .value
                                                               .values
                                                               .filter { it.state == Download.STATE_COMPLETED }
                                                               .fastMap { it.request.id }
                Database.songTable
                        .sortAll( songSort.sortBy, songSort.sortOrder )
                        .map { list ->
                            list.fastFilter { it.id in downloaded }
                        }
            }

            BuiltInPlaylist.Offline -> Database.formatTable
                                               .sortAllWithSongs( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                                               .map { list ->
                                                   list.fastFilter {
                                                       val contentLength = it.format.contentLength ?: return@fastFilter false
                                                       cache.isCached( it.song.id, 0, contentLength )
                                                   }.map( FormatWithSong::song )
                                               }

            BuiltInPlaylist.Favorites -> Database.songTable.sortFavorites( songSort.sortBy, songSort.sortOrder )

            BuiltInPlaylist.Top -> Database.eventTable
                                           .findSongsMostPlayedBetween(
                                               from = topPlaylists.period.timeStampInMillis(),
                                               limit = maxTopPlaylistItems.toInt()
                                           )
                                           .map { list ->
                                               // Exclude songs with duration higher than what [excludeSongWithDurationLimit] is
                                               list.fastFilter { song ->
                                                   excludeSongWithDurationLimit == DurationInMinutes.Disabled
                                                           || song.durationText.toDuration().inWholeMilliseconds < excludeSongWithDurationLimit.asMillis
                                               }
                                           }

            BuiltInPlaylist.OnDevice -> flowOf( emptyList() )
        }

        retrievedSongs.flowOn( Dispatchers.IO )
                      .distinctUntilChanged()
                      .collect {
                          items = it

                          /*
                           * Scrolling to the top is a *consequence* of new data, never a
                           * precondition for it.
                           *
                           * As an `onEach` upstream of this `collect` it suspended until the list
                           * had been laid out. That is fine while the list is on screen and fatal
                           * when it is not: the empty state below returns early instead of
                           * composing the `LazyColumn`, so on a first visit there was no layout to
                           * wait for, the collection never resumed, `items` never arrived, and the
                           * empty state kept itself alive. Opening the library showed "no songs"
                           * over a full database until a filter chip was tapped.
                           *
                           * Launched separately so a suspended scroll can never block delivery
                           * again, and swallowing failures because a scroll that cannot happen is
                           * not worth losing the data over.
                           */
                          launch { runCatching { lazyListState.scrollToItem( 0, 0 ) } }

                          /*
                           * Loading ends when the data arrives — not when [items] happens to
                           * change value.
                           *
                           * Clearing this was previously left to the filtering effect below,
                           * which is keyed on `items`. If a category resolved to a list equal to
                           * the one already held, that effect never re-ran, so the flag stayed
                           * latched and the screen showed skeleton placeholders forever. Two
                           * empty categories in a row was enough to trigger it, which is exactly
                           * the case a new user hits first.
                           */
                          isLoading = false
                      }
    }

    LaunchedEffect( items, search.input ) {
    items.filter { !parentalControlEnabled || !it.isExplicit }
         .filter {
             // Without cleaning, user can search explicit songs with "e:"
             // I kinda want this to be a feature, but it seems unnecessary
             val containsTitle = search appearsIn it.cleanTitle()
             val containsArtist = search appearsIn it.cleanArtistsText()

             containsTitle || containsArtist
         }
        .let {
            itemsOnDisplay.clear()
            itemsOnDisplay.addAll( it )

            // Deliberately does not clear [isLoading]. This effect also runs on the first
            // composition, when `items` is still empty, and clearing the flag there let the empty
            // state render before the query had even been asked. Only the collector above knows
            // whether the data has actually arrived.
        }
    }

    LaunchedEffect( builtInPlaylist ) {
        val firstButton = if( builtInPlaylist == BuiltInPlaylist.Top ) topPlaylists else songSort
        buttons.add( 0, firstButton )
        // Appended rather than pushed to positions 3 and 4. These are bulk operations — one of
        // them deletes every download — and they were outranking Shuffle for prime toolbar space.
        // Rare and destructive belongs in the labelled menu, where it is also read before it is
        // tapped.
        buttons.add( downloadAllDialog )
        buttons.add( deleteDownloadsDialog )
        buttons.add( exportDialog )
    }

    //<editor-fold defaultstate="collapsed" desc="Dialog Renders">
    exportDialog.Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()
    //</editor-fold>

    val currentMediaItem by player.currentMediaItemState.collectAsState()
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }

    // An empty list used to render as blank space, which reads as "broken" or "still loading"
    // rather than "nothing here yet". Each category explains itself and, where there is a sensible
    // next step, offers it.
    if( !isLoading && itemsOnDisplay.isEmpty() ) {
        val copy: Pair<Int, Int> = when( builtInPlaylist ) {
            BuiltInPlaylist.Favorites  -> R.string.empty_favorites_title to R.string.empty_favorites_description
            BuiltInPlaylist.Offline    -> R.string.empty_cached_title to R.string.empty_cached_description
            BuiltInPlaylist.Downloaded -> R.string.empty_downloaded_title to R.string.empty_downloaded_description
            BuiltInPlaylist.Top        -> R.string.empty_top_title to R.string.empty_top_description
            BuiltInPlaylist.OnDevice   -> R.string.empty_on_device_title to R.string.empty_on_device_description
            BuiltInPlaylist.All        -> R.string.empty_songs_title to R.string.empty_songs_description
        }
        val (titleId, descriptionId) = copy

        EmptyState(
            iconId = R.drawable.musical_notes,
            titleId = titleId,
            descriptionId = descriptionId,
            // "On device" is filled by the filesystem, not by the app, so search would be a dead
            // end there — every other category is something searching can actually populate.
            actionLabelId = R.string.empty_action_find_music.takeIf { builtInPlaylist != BuiltInPlaylist.OnDevice },
            onAction = { NavRoutes.search.navigateHere( navController ) }
        )

        return
    }

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = !isLoading,
        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
    ) {
        if( isLoading )
            items( 20, null ) { SongItem.Placeholder() }

        itemsIndexed(
            items = itemsOnDisplay,
            key = { _, song -> song.id }
        ) { index, song ->
            val mediaItem = song.asMediaItem

            val isLocal by remember { derivedStateOf { mediaItem.isLocal } }
            val isDownloaded = isLocal || isDownloadedSong( mediaItem.mediaId )

            SwipeablePlaylistItem(
                mediaItem = mediaItem,
                onPlayNext = { player.addNext( mediaItem ) },
                onDownload = {
                    if( builtInPlaylist != BuiltInPlaylist.OnDevice ) {
                        cache.removeResource(mediaItem.mediaId)
                        Database.asyncTransaction {
                            formatTable.updateContentLengthOf( mediaItem.mediaId )
                        }
                        if ( !isLocal )
                            manageDownload(
                                context = context,
                                mediaItem = mediaItem,
                                downloadState = isDownloaded
                            )
                    }
                },
                onEnqueue = {
                    player.enqueue(mediaItem)
                }
            ) {
                SongItem.Render(
                    song = song,
                    hapticFeedback = hapticFeedback,
                    isPlaying = song.shallowCompare( currentMediaItem ),
                    values = songItemValues,
                    itemSelector = itemSelector,
                    modifier = Modifier.animateItem(),
                    thumbnailOverlay = {
                        if ( songSort.sortBy == SongSortBy.TOTAL_PLAY_TIME || builtInPlaylist == BuiltInPlaylist.Top ) {
                            var text = song.formattedTotalPlayTime
                            var typography = LumaType.Numeral
                            var alignment = Alignment.BottomCenter

                            if( builtInPlaylist == BuiltInPlaylist.Top ) {
                                text = (index + 1).toString()
                                typography = LumaType.Row
                                alignment = Alignment.Center
                            }

                            BasicText(
                                text = text,
                                style = typography.semiBold.center.color(LumaColor.Ink),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(alignment)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                LumaColor.Ground
                                            )
                                        ),
                                        shape = thumbnailShape()
                                    )
                            )
                        }
                    },
                    onClick = {
                        search.hideIfEmpty()

                        player.stopRadio()

                        val selectedSongs = getSongs()
                        if( song in selectedSongs )
                            player.forcePlayAtIndex(
                                selectedSongs.fastMap( Song::asMediaItem ),
                                selectedSongs.indexOf( song )
                            )
                        else
                            player.forcePlayAtIndex(
                                itemsOnDisplay.fastMap( Song::asMediaItem ),
                                index
                            )
                    },
                    onLongClick = {
                        val page = if( song.isLocal ) MenuPage.LocalSong(mediaItem) else MenuPage.Song(mediaItem)
                        menu.show( page, true )
                    }
                )
            }
        }
    }
}