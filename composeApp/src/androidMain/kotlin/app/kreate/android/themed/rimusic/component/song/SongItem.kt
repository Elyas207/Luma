package app.kreate.android.themed.rimusic.component.song

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import app.kreate.android.Preferences
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Visual
import app.kreate.android.utils.innertube.toSong
import app.kreate.android.utils.scrollingText
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.DownloadedStateMedia
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.ui.components.MusicAnimation
import it.fast4x.rimusic.ui.styling.Appearance
import it.fast4x.rimusic.ui.styling.ColorPalette
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.Typography
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.ui.styling.favoritesOverlay
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.downloadedStateMedia
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import kotlinx.coroutines.Dispatchers
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import org.koin.java.KoinJavaComponent.inject

object SongItem: Visual() {

    const val DOWNLOAD_ICON_SIZE = 20
    const val BADGE_SIZE = 18
    const val BADGES_SPACING = 3
    const val LIKE_ICON_SIZE = 12

    val itemShape: Shape by lazy { RoundedCornerShape(10.dp) }
    override val thumbnailRoundnessPercent: Preferences.Int = Preferences.SONG_THUMBNAIL_ROUNDNESS_PERCENT

    override fun thumbnailSize() = DpSize(Preferences.SONG_THUMBNAIL_SIZE.value.dp, Preferences.SONG_THUMBNAIL_SIZE.value.dp)

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param title name of the song, must **not** contain artifacts or prefixes
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Title(
        title: String,
        values: Values,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = title,
            style = values.titleTextStyle,
            color = values.titleColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = modifier.scrollingText()
        )

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param artistsText name of the artists, must **not** contain artifacts or prefixes
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Artists(
        artistsText: String,
        values: Values,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = artistsText,
            style = values.artistsTextStyle,
            color = values.artistsColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = modifier.scrollingText()
        )

    /**
     * Text is clipped if exceeds length limit, plus,
     * conditional marquee effect is applied by default.
     *
     * @param duration song's length, `null` value will be converted into "`--:--`"
     * @param values contains [TextStyle] and [Color] configs for this component
     * @param modifier the [Modifier] to be applied to this layout node
     *
     * @see scrollingText
     */
    @Composable
    fun Duration(
        duration: String?,
        values: Values,
        modifier: Modifier = Modifier
    ) =
        Text(
            text = duration
                ?: if( Preferences.SONG_EMPTY_DURATION_PLACEHOLDER.value ) "--:--" else "",
            style = values.durationTextStyle,
            color = values.durationColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = modifier.scrollingText()
        )

    /**
     * Stateful button to display current cache status of a song.
     *
     * - [R.drawable.download_progress] during download process
     * - [R.drawable.download] cached if lit up, or neither cached or downloaded
     * - [R.drawable.downloaded] when song is downloaded
     */
    @UnstableApi
    @Composable
    private fun <T> CacheAndDownloadIcon(
        songId: String,
        song: T,
        values: Values,
        handler: (T, Boolean) -> Unit,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {}
    ) {
        val cacheState = downloadedStateMedia( songId )
        val downloadState = getDownloadState( songId )

        val iconId = when( downloadState ) {
            Download.STATE_DOWNLOADING  -> R.drawable.download_progress
            Download.STATE_REMOVING     -> R.drawable.download
            else                        -> cacheState.androidIconId
        }
        val color = when( cacheState ) {
            DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED   -> values.uncachedColor
            DownloadedStateMedia.CACHED                     -> values.cachedColor
            else                                            -> values.downloadedColor
        }

        Icon(
            painter = painterResource( iconId ),
            contentDescription = stringResource( R.string.download ),
            tint = color,
            modifier = modifier.size( DOWNLOAD_ICON_SIZE.dp )
                               .clickable {
                                   onClick()

                                   Database.asyncTransaction {
                                       formatTable.deleteBySongId( songId )
                                   }

                                   handler( song, true )
                               }
        )
    }

    /**
     * Display badges such as "playlist", "explicit", etc.
     */
    @Composable
    fun Badges(
        songId: String,
        isRecommended: Boolean,
        isInPlaylistScreen: Boolean,
        isExplicit: Boolean,
        values: Values,
        modifier: Modifier = Modifier
    ) {
        @Composable
        fun Badge(
            @DrawableRes iconId: Int,
            color: Color,
            contentDescription: String?,
            modifier: Modifier = Modifier,
            onLongClick: () -> Unit = { contentDescription?.also(Toaster::i ) }
        ) =
            Icon(
                painter = painterResource( iconId ),
                contentDescription = contentDescription,
                tint = color,
                modifier = modifier
                    .size(BADGE_SIZE.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongClick
                    )
            )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( BADGES_SPACING.dp ),
            modifier = modifier
        ) {
            if( isRecommended )
                Badge(
                    iconId = R.drawable.smart_shuffle,
                    color = values.recommendedBadgeColor,
                    contentDescription = stringResource( R.string.info_added_by_smart_recommendations )
                )

            // Show icon if song belongs to a playlist,
            // except for when it's in a playlist.
            val showInPlaylistIndicator by Preferences.SHOW_PLAYLIST_INDICATOR
            if( !isInPlaylistScreen && showInPlaylistIndicator ) {

                val isExistedInAPlaylist by remember {
                    Database.songPlaylistMapTable.isMapped( songId )
                }.collectAsState( false, Dispatchers.IO )

                if( isExistedInAPlaylist )
                    Badge(
                        iconId = R.drawable.add_in_playlist,
                        color = values.inPlaylistBadgeColor,
                        contentDescription = stringResource( R.string.playlistindicatorinfo2 )
                    )
            }

            if( isExplicit ) {
                val description = stringResource( R.string.info_explicit_song )
                Badge(
                    iconId = R.drawable.explicit,
                    color = values.explicitBadgeColor,
                    contentDescription = description
                ) { Toaster.w( description ) }
            }
        }
    }

    @Composable
    fun Thumbnail(
        thumbnailUrl: String?,
        values: Values,
        modifier: Modifier = Modifier,
        isPlaying: Boolean = false ,
        isLiked: Boolean = false,
        showThumbnail: Boolean = true,
        sizeDp: DpSize = thumbnailSize(),
        thumbnailOverlay: @Composable BoxScope.() -> Unit = {}
    ) =
        Thumbnail(
            url = thumbnailUrl,
            modifier = modifier,
            showThumbnail = showThumbnail,
            contentScale = ContentScale.FillHeight,
            sizeDp = sizeDp,
            contentAlignment = Alignment.Center
        ) {
            if( isPlaying )
                MusicAnimation(
                    color = values.nowPlayingIndicatorColor,
                    modifier = Modifier.size( sizeDp / 2 )
                )

            thumbnailOverlay()

            if( isLiked )
                Icon(
                    painter = Preferences.LIKE_ICON.value.likedIcon,
                    contentDescription = null,
                    tint = values.likedIconColor,
                    modifier = Modifier.size( LIKE_ICON_SIZE.dp )
                                       .align( Alignment.BottomStart )
                                       .absoluteOffset( x = (-8).dp )
                )
        }

    @Composable
    fun Structure(
        thumbnail: @Composable RowScope.() -> Unit,
        firstLine: @Composable RowScope.() -> Unit,
        secondLine: @Composable RowScope.() -> Unit,
        modifier: Modifier = Modifier,
        trailingContent: @Composable RowScope.() -> Unit = {}
    ) =
        /*
         * Retyping this row was not going to be enough on its own — a row of
         * `[thumb][title][artist][duration][download]` is still that row in a different font, and
         * that exact observation has already been made about this project once.
         *
         * So the row's proportions change too. It breathes: roughly twice the vertical padding, and
         * a wider gap between the artwork and the text. A screen of six generous rows is a visibly
         * different object from a screen of twelve dense ones, which is a difference a squint test
         * registers and a font change is not.
         *
         * The density this gives up is deliberate. Fitting more rows on screen only helps if you
         * are scanning for one known item, and that is what search is for; browsing a list you can
         * actually see is the common case.
         */
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 16.dp ),
            modifier = modifier.fillMaxWidth()
                               .padding(
                                   vertical = Dimensions.itemsVerticalPadding * 2,
                                   horizontal = 22.dp
                               )
        ) {
            thumbnail()

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy( 3.dp ),
                modifier = Modifier.weight( 1f )
            ) {
                Row( verticalAlignment = Alignment.CenterVertically, content = firstLine )
                Row( verticalAlignment = Alignment.CenterVertically, content = secondLine )
            }

            trailingContent()
        }

    @Composable
    fun Placeholder(
        thumbnailSize: DpSize = DpSize(Dimensions.thumbnails.song, Dimensions.thumbnails.song),
        modifier: Modifier = Modifier
    ) =
        Structure(
            modifier = modifier,
            thumbnail = {
                Box(
                    Modifier.clip( thumbnailShape() )
                            .size( thumbnailSize )
                            .shimmerEffect()
                )
            },
            firstLine = {
                Title(
                    title = "",
                    values = Values.unspecified,
                    modifier = Modifier.fillMaxWidth()
                                       .shimmerEffect()
                )
            },
            secondLine = {
                Artists(
                    artistsText = "",
                    values = Values.unspecified,
                    modifier = Modifier.fillMaxWidth( .6f )
                )
            }
        )

    private fun Modifier.songItemModifier(
        isPlaying: Boolean,
        values: Values,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ): Modifier =
        clip( itemShape )
            .conditional( isPlaying ) {
                background( values.nowPlayingOverlayColor )
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )


    @kotlin.OptIn(ExperimentalFoundationApi::class)
    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        song: Song,
        hapticFeedback: HapticFeedback,
        isPlaying: Boolean,
        values: Values,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        isInPlaylistScreen: Boolean = false,
        itemSelector: ItemSelector<Song>? = null,
        isRecommended: Boolean = false,
        showThumbnail: Boolean = true,
        trailingContent: @Composable (RowScope.() -> Unit)? = null,
        thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
        onClick: () -> Unit = {}
    ) {
        Structure(
            thumbnail = {
                Thumbnail(
                    showThumbnail = showThumbnail,
                    thumbnailUrl = song.cleanThumbnailUrl(),
                    isLiked = song.likedAt != null,
                    isPlaying = isPlaying,
                    values = values,
                    thumbnailOverlay = thumbnailOverlay
                )
            },
            firstLine = {
                Badges(
                    songId = song.id,
                    isRecommended = isRecommended,
                    isInPlaylistScreen = isInPlaylistScreen,
                    isExplicit = song.isExplicit,
                    values = values
                )

                Title( song.cleanTitle(), values, Modifier.weight( 1f ) )
            },
            secondLine = {
                Artists(
                    artistsText = song.cleanArtistsText(),
                    values = values,
                    modifier = Modifier.weight( 1f )
                )
                Duration(
                    duration = song.durationText,
                    values = values,
                    modifier = Modifier.padding( horizontal = 5.dp )
                                       // Text is a bit shorter, adding this to bring
                                       // it to the bottom for better view
                                       .align( Alignment.Bottom )
                )

                if( !song.isLocal )
                    CacheAndDownloadIcon( song.id, song, values, MyDownloadHelper::handleDownload , modifier ) {
                        val cache: Cache by inject(Cache::class.java, CacheType.CACHE)
                        cache.removeResource( song.id )
                    }
            },
            trailingContent = {
                itemSelector?.CheckBox( song )
                trailingContent?.invoke( this )
            },
            modifier = modifier.songItemModifier( isPlaying, values, onClick ) {
                hapticFeedback.performHapticFeedback( HapticFeedbackType.LongPress )
                onLongClick.invoke()
            }
        )
    }

    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        mediaItem: MediaItem,
        hapticFeedback: HapticFeedback,
        isPlaying: Boolean,
        values: Values,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        isInPlaylistScreen: Boolean = false,
        itemSelector: ItemSelector<Song>? = null,
        isRecommended: Boolean = false,
        showThumbnail: Boolean = true,
        trailingContent: @Composable RowScope.() -> Unit = {},
        thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
        onClick: () -> Unit = {}
    ) =
        Render(
            song = mediaItem.asSong,
            hapticFeedback = hapticFeedback,
            isPlaying = isPlaying,
            values = values,
            modifier = modifier,
            isInPlaylistScreen = isInPlaylistScreen,
            itemSelector = itemSelector,
            isRecommended = isRecommended,
            showThumbnail = showThumbnail,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            thumbnailOverlay = thumbnailOverlay,
            onClick = onClick
        )

    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        innertubeSong: Innertube.SongItem,
        hapticFeedback: HapticFeedback,
        isPlaying: Boolean,
        values: Values,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        isInPlaylistScreen: Boolean = false,
        itemSelector: ItemSelector<Song>? = null,
        isRecommended: Boolean = false,
        showThumbnail: Boolean = true,
        trailingContent: @Composable RowScope.() -> Unit = {},
        thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
        onClick: () -> Unit = {}
    ) =
        Render(
            song = innertubeSong.asSong,
            hapticFeedback = hapticFeedback,
            isPlaying = isPlaying,
            values = values,
            modifier = modifier,
            isInPlaylistScreen = isInPlaylistScreen,
            itemSelector = itemSelector,
            isRecommended = isRecommended,
            showThumbnail = showThumbnail,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            thumbnailOverlay = thumbnailOverlay,
            onClick = onClick
        )

    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        innertubeSong: InnertubeSong,
        hapticFeedback: HapticFeedback,
        isPlaying: Boolean,
        values: Values,
        onLongClick: () -> Unit,
        modifier: Modifier = Modifier,
        isInPlaylistScreen: Boolean = false,
        itemSelector: ItemSelector<Song>? = null,
        isRecommended: Boolean = false,
        showThumbnail: Boolean = true,
        trailingContent: @Composable RowScope.() -> Unit = {},
        thumbnailOverlay: @Composable BoxScope.() -> Unit = {},
        onClick: () -> Unit = {}
    ) =
        Render(
            song = innertubeSong.toSong,
            hapticFeedback = hapticFeedback,
            isPlaying = isPlaying,
            values = values,
            modifier = modifier,
            isInPlaylistScreen = isInPlaylistScreen,
            itemSelector = itemSelector,
            isRecommended = isRecommended,
            showThumbnail = showThumbnail,
            onLongClick = onLongClick,
            trailingContent = trailingContent,
            thumbnailOverlay = thumbnailOverlay,
            onClick = onClick
        )

    @OptIn(UnstableApi::class)
    @Composable
    fun Render(
        innertubeVideo: Innertube.VideoItem,
        hapticFeedback: HapticFeedback,
        isPlaying: Boolean,
        values: Values,
        thumbnailSizeDp: DpSize,
        modifier: Modifier = Modifier,
        showThumbnail: Boolean = true,
        onLongClick: (() -> Unit)? = null,
        trailingContent: @Composable RowScope.() -> Unit = {},
        onClick: () -> Unit = {}
    ) =
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy( 12.dp ),
            modifier = modifier.fillMaxWidth()
                               .songItemModifier(isPlaying, values, onClick) {
                                   hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                   onLongClick?.invoke()
                               }
                               .padding(
                                   vertical = Dimensions.itemsVerticalPadding,
                                   horizontal = 16.dp
                               )
        ) {
            Thumbnail(
                showThumbnail = showThumbnail,
                thumbnailUrl = innertubeVideo.thumbnail?.url,
                isPlaying = isPlaying,
                values = values,
                sizeDp = thumbnailSizeDp,
                thumbnailOverlay = {
                    Duration(
                        duration = innertubeVideo.durationText.orEmpty(),
                        values = values,
                        modifier = Modifier.padding( all = 4.dp )
                                           .background(
                                               color = LumaColor.Ground,
                                               shape = itemShape
                                           )
                                           .padding( horizontal = 4.dp, vertical = 2.dp )
                                           .align( Alignment.BottomEnd )
                    )
                }
            )

            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.requiredHeight( thumbnailSizeDp.height )
                                   .padding( vertical = 5.dp )
            ) {
                Title( innertubeVideo.info?.name.orEmpty(), values, Modifier.fillMaxWidth() )
                Artists(
                    artistsText = innertubeVideo.authors
                                                ?.fastJoinToString { it.name.orEmpty() }
                                                .orEmpty(),
                    values = values,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer( modifier = Modifier.weight(1f) )

                Duration(
                    duration = innertubeVideo.viewsText.orEmpty().trim(),
                    values = values,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            trailingContent()
        }

    data class Values(
        val nowPlayingOverlayColor: Color,
        val nowPlayingIndicatorColor: Color,
        val titleTextStyle: TextStyle,
        val titleColor: Color,
        val artistsTextStyle: TextStyle,
        val artistsColor: Color,
        val durationTextStyle: TextStyle,
        val durationColor: Color,
        val uncachedColor: Color,
        val cachedColor: Color,
        val downloadedColor: Color,
        val recommendedBadgeColor: Color,
        val inPlaylistBadgeColor: Color,
        val explicitBadgeColor: Color,
        val likedIconColor: Color,
    ) {
        companion object {
            val unspecified: Values by lazy {
                val textStyle = TextStyle()
                Values(
                    nowPlayingOverlayColor = Color.Transparent,
                    nowPlayingIndicatorColor = Color.Transparent,
                    titleTextStyle = textStyle,
                    titleColor = Color.Transparent,
                    artistsTextStyle = textStyle,
                    artistsColor = Color.Transparent,
                    durationTextStyle = textStyle,
                    durationColor = Color.Transparent,
                    uncachedColor = Color.Transparent,
                    cachedColor = Color.Transparent,
                    downloadedColor = Color.Transparent,
                    recommendedBadgeColor = Color.Transparent,
                    inPlaylistBadgeColor = Color.Transparent,
                    explicitBadgeColor = Color.Transparent,
                    likedIconColor = Color.Transparent
                )
            }

            /**
             * Three ranks of information, set as three ranks of type.
             *
             * The title and the artist used to share one style — `xs.semiBold`, byte for byte —
             * separated only by a slightly dimmer grey. Two lines of identical weight read as one
             * block, so scanning a list meant reading every row instead of skimming the titles,
             * and the row carried no hierarchy for a theme to express.
             *
             * Now the title leads (larger, medium weight — heavy enough to anchor the row, not so
             * heavy it shouts on a screen of forty), the artist supports it a size down at normal
             * weight, and the duration recedes to the disabled colour: it is reference detail you
             * consult, never something you scan for.
             */
            fun from( colorPalette: ColorPalette, typography: Typography ) =
                Values(
                    // The inherited "favourites overlay" was a khaki wash that belonged to the old
                    // palette; over Luma's warm black it reads as a stain rather than a highlight.
                    // A low-alpha wash of the app's own accent marks the playing row without
                    // repainting it, which is all a highlight has to do.
                    nowPlayingOverlayColor = LumaColor.Ember.copy( alpha = 0.16f ),
                    nowPlayingIndicatorColor = LumaColor.Ember,
                    // The title carries the app's identity here, because this row is the single
                    // most repeated object in the interface — it is in search results, every
                    // library section, every album, playlist, artist, the history and the queue.
                    // A sans title in this one component is enough to make the whole app read as
                    // stock, however the screens around it are composed.
                    titleTextStyle = LumaType.Tile,
                    titleColor = LumaColor.Ink,
                    artistsTextStyle = LumaType.Meta,
                    artistsColor = LumaColor.InkSoft,
                    // Wide-tracked micro caps rather than a right-aligned column of digits. The
                    // duration is reference detail you consult once, never something you scan a
                    // list for, and setting it as a number in the same family as the title gave it
                    // a visual weight it has not earned.
                    durationTextStyle = LumaType.Numeral,
                    durationColor = LumaColor.InkFaint,
                    uncachedColor = LumaColor.InkFaint,
                    cachedColor = LumaColor.InkSoft,
                    downloadedColor = LumaColor.Ink,
                    recommendedBadgeColor = LumaColor.Ember,
                    inPlaylistBadgeColor = LumaColor.Ember,
                    explicitBadgeColor = Color.White,
                    likedIconColor = LumaColor.Ember
                )

            fun from( appearance: Appearance ) =
                from( appearance.colorPalette, appearance.typography )
        }
    }
}