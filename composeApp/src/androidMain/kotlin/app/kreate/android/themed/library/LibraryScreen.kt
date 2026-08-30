package app.kreate.android.themed.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import app.kreate.android.themed.luma.LumaAtmosphere
import app.kreate.android.themed.luma.rememberArtworkAccent
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaLabel
import app.kreate.android.themed.luma.LumaRingButton
import app.kreate.android.themed.luma.LumaShape
import app.kreate.android.themed.luma.LumaType
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * The library as something you browse, not something you file.
 *
 * The screen this replaces was a taxonomy switch: pick Songs / Artists / Albums / Playlists from a
 * tab strip, get a flat list of rows with a duration and a download button on each. That layout
 * answers "where is this filed" and is optimised for *managing* a collection — but managing is the
 * rare task. The common one is "show me what I have so I can pick something", and for that a list
 * of text rows is the worst possible surface: it hides the artwork, which is the thing people
 * actually recognise.
 *
 * So the tab strip is gone entirely and the four categories become four shelves on one vertical
 * scroll, artwork first, text second. Nothing is nested; everything is visible by scrolling.
 *
 * **Shape carries type.** Artists are circles, albums are squares, playlists are rounded tiles,
 * songs are the only things with a line of text beneath a small tile. You can tell what kind of
 * thing you are looking at from its silhouette alone, before reading a word — which is what makes
 * a mixed vertical scroll legible without a label on every group.
 *
 * The management tools — sort, filter chips, multi-select, bulk download — have not been deleted.
 * They live one level down, behind each shelf's header, which is where a rare and destructive set
 * of actions belongs. They were previously in front of everyone, permanently, on the way to the
 * music.
 */
@Composable
fun LibraryScreen(
    onPlaySong: ( Song ) -> Unit,
    onOpenSection: ( Int ) -> Unit,
    onOpenArtist: ( Artist ) -> Unit,
    onOpenAlbum: ( Album ) -> Unit,
    onOpenPlaylist: ( PlaylistPreview ) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val artists by Database.artistTable.allWithSongs( SHELF_LIMIT ).collectAsState( emptyList() )
    val albums by Database.albumTable.allWithSongs( SHELF_LIMIT ).collectAsState( emptyList() )
    val playlists by Database.playlistTable.allAsPreview( SHELF_LIMIT ).collectAsState( emptyList() )
    val songs by Database.songTable.all( SONG_PREVIEW * 3 ).collectAsState( emptyList() )

    // A tablet is roughly twice a phone across and no taller, so phone-sized tiles leave the
    // shelves looking like a phone layout that was stretched. Bigger tiles, and more songs before
    // the fold, because the extra room is vertical too once the tiles stop being the constraint.
    val isWide = LocalConfiguration.current.screenWidthDp >= 720
    val artistSize = if ( isWide ) 132.dp else 104.dp
    val tileSize = if ( isWide ) 184.dp else 144.dp

    // The library has no single "now" to take its colour from, so it borrows the first thing on the
    // shelves. Two people's libraries are lit differently, and one person's shifts as theirs grows.
    val accent by rememberArtworkAccent(
        songs.firstOrNull()?.cleanThumbnailUrl() ?: albums.firstOrNull()?.thumbnailUrl
    )

    Box( modifier.fillMaxSize().background( LumaColor.Ground ) ) {

    // Quieter than the player's. This screen is read, and a strong wash under a long scroll of
    // artwork competes with the artwork for the same attention.
    LumaAtmosphere( accent, Modifier.fillMaxSize(), intensity = 0.45f )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues( bottom = 120.dp )
    ) {

        item { LibraryHeader( onOpenSearch ) }

        if ( artists.isNotEmpty() )
            item {
                Shelf( "Artists", TAB_ARTISTS, onOpenSection ) {
                    items( artists, key = Artist::id ) { artist ->
                        // Circle. A person is not a rectangle, and the round silhouette is what
                        // separates this shelf from the two beneath it at a glance.
                        Piece(
                            title = artist.name.orEmpty(),
                            thumbnailUrl = artist.thumbnailUrl,
                            shape = CircleShape,
                            size = artistSize,
                            centred = true,
                            onClick = { onOpenArtist( artist ) }
                        )
                    }
                }
            }

        if ( albums.isNotEmpty() )
            item {
                Shelf( "Albums", TAB_ALBUMS, onOpenSection ) {
                    items( albums, key = Album::id ) { album ->
                        // Square, barely rounded — a record sleeve is a square object and the
                        // hard corner is what makes it read as one next to the circles above.
                        Piece(
                            title = album.title.orEmpty(),
                            subtitle = album.authorsText,
                            thumbnailUrl = album.thumbnailUrl,
                            shape = RoundedCornerShape( 4.dp ),
                            size = tileSize,
                            onClick = { onOpenAlbum( album ) }
                        )
                    }
                }
            }

        if ( playlists.isNotEmpty() )
            item {
                Shelf( "Playlists", TAB_PLAYLISTS, onOpenSection ) {
                    items( playlists, key = { it.playlist.id } ) { preview ->
                        // Generously rounded: a playlist is the one thing here you made rather
                        // than something that arrived as a finished object.
                        PlaylistPiece( preview, tileSize ) { onOpenPlaylist( preview ) }
                    }
                }
            }

        if ( songs.isNotEmpty() ) {
            item {
                ShelfHeader( "Songs", TAB_SONGS, onOpenSection )
            }

            // A short column rather than a shelf. Songs are the one category with no artwork of
            // their own worth a 144dp tile — the cover belongs to the album — and they are the
            // category you are most often looking for a *specific* member of, which reading down
            // a column serves and swiping across a shelf does not.
            // Paired up on a wide screen. One column of 52dp rows across 1280dp of tablet leaves
            // three quarters of the line empty and makes the songs look like an afterthought below
            // the shelves.
            val preview = songs.take( if ( isWide ) SONG_PREVIEW * 2 else SONG_PREVIEW )

            if ( isWide )
                items( preview.chunked( 2 ), key = { it.first().id } ) { pair ->
                    Row( Modifier.fillMaxWidth() ) {
                        pair.forEach { song ->
                            Box( Modifier.weight( 1f ) ) {
                                SongLine( song ) { onPlaySong( song ) }
                            }
                        }
                        // Keeps a lone trailing song at half width instead of stretching it.
                        if ( pair.size == 1 ) Spacer( Modifier.weight( 1f ) )
                    }
                }
            else
                items( preview, key = Song::id ) { song ->
                    SongLine( song ) { onPlaySong( song ) }
                }

            if ( songs.size > SONG_PREVIEW )
                item {
                    Text(
                        text = "All ${songs.size} songs",
                        style = LumaType.Tile,
                        color = LumaColor.InkSoft,
                        modifier = Modifier
                            .padding( horizontal = 12.dp )
                            .clip( RoundedCornerShape( 10.dp ) )
                            .clickable { onOpenSection( TAB_SONGS ) }
                            .padding( horizontal = 12.dp, vertical = 14.dp )
                    )
                }
        }

        if ( artists.isEmpty() && albums.isEmpty() && playlists.isEmpty() && songs.isEmpty() )
            item { EmptyLibrary( onOpenSearch ) }
    }
    }
}

@Composable
private fun LibraryHeader( onOpenSearch: () -> Unit ) = Row(
    Modifier
        .fillMaxWidth()
        .padding( start = 24.dp, end = 16.dp, top = 26.dp, bottom = 10.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = "Library",
        style = LumaType.Hero,
        color = LumaColor.Ink,
        modifier = Modifier.weight( 1f )
    )

    LumaRingButton(
        iconRes = R.drawable.search,
        contentDescription = "Search",
        onClick = onOpenSearch,
        diameter = 46.dp
    )
}

/**
 * A titled horizontal group.
 *
 * The header is the way in to the full section — and the *only* way, which is deliberate. Making
 * the group title the door means there is no separate "see all" control competing with it, and it
 * puts the sort/filter machinery exactly one tap from where you would ask for it.
 */
@Composable
private fun Shelf(
    title: String,
    tabIndex: Int,
    onOpenSection: ( Int ) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) = Column( Modifier.padding( top = 18.dp ) ) {

    ShelfHeader( title, tabIndex, onOpenSection )

    LazyRow(
        contentPadding = PaddingValues( horizontal = 24.dp ),
        horizontalArrangement = Arrangement.spacedBy( 16.dp ),
        content = content
    )
}

@Composable
private fun ShelfHeader(
    title: String,
    tabIndex: Int,
    onOpenSection: ( Int ) -> Unit
) = Row(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 12.dp )
        .clip( RoundedCornerShape( 12.dp ) )
        .clickable { onOpenSection( tabIndex ) }
        .padding( start = 12.dp, end = 12.dp, top = 10.dp, bottom = 14.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = title,
        style = LumaType.Section,
        color = LumaColor.Ink,
        modifier = Modifier.weight( 1f )
    )

    Image(
        painter = painterResource( R.drawable.chevron_forward ),
        contentDescription = null,
        colorFilter = ColorFilter.tint( LumaColor.InkFaint ),
        modifier = Modifier.size( 16.dp )
    )
}

/**
 * One item on a shelf: artwork in the shape of its kind, with its name beneath.
 *
 * [shape] is the whole point — see the class docs. It is passed in rather than derived so each
 * shelf states its own silhouette at the call site, where it is read alongside the data.
 */
@Composable
private fun Piece(
    title: String,
    thumbnailUrl: String?,
    shape: Shape,
    size: Dp,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    centred: Boolean = false,
    onClick: () -> Unit
) = Column(
    modifier
        .width( size )
        .clickable( onClick = onClick ),
    horizontalAlignment = if ( centred ) Alignment.CenterHorizontally else Alignment.Start
) {
    Artwork(
        title = title,
        thumbnailUrl = thumbnailUrl,
        modifier = Modifier
            .size( size )
            .clip( shape )
    )

    Spacer( Modifier.height( 10.dp ) )

    Text(
        text = title,
        style = LumaType.Tile,
        color = LumaColor.Ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if ( centred ) TextAlign.Center else TextAlign.Start
    )

    if ( subtitle != null )
        Text(
            text = subtitle,
            style = LumaType.Meta,
            color = LumaColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if ( centred ) TextAlign.Center else TextAlign.Start
        )
}

/**
 * A playlist, wearing its contents.
 *
 * The tile is a 2×2 mosaic of the first four covers rather than one borrowed cover, because a
 * playlist is a set and a single cover claims it is a record. It also keeps two playlists apart
 * when both happen to open with the same track. Fewer than four tracks fall back to whatever is
 * there, and an empty playlist keeps the initial.
 */
@Composable
private fun PlaylistPiece( preview: PlaylistPreview, size: Dp, onClick: () -> Unit ) {

    val covers by remember( preview.playlist.id ) {
        Database.playlistTable.coverUrls( preview.playlist.id )
    }.collectAsState( emptyList() )

    val count = preview.songCount

    Column(
        Modifier
            .width( size )
            .clickable( onClick = onClick )
    ) {
        Box(
            Modifier
                .size( size )
                .clip( LumaShape.Arch )
                .background( LumaColor.Raised ),
            contentAlignment = Alignment.Center
        ) {
            if ( covers.size >= 4 )
                Column {
                    repeat( 2 ) { row ->
                        Row {
                            repeat( 2 ) { column ->
                                Image(
                                    painter = ImageFactory.rememberAsyncImagePainter(
                                        thumbnailUrl = covers[ row * 2 + column ]
                                    ),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size( size / 2 )
                                )
                            }
                        }
                    }
                }
            else
                Artwork(
                    title = preview.playlist.name,
                    thumbnailUrl = covers.firstOrNull(),
                    modifier = Modifier.fillMaxSize()
                )
        }

        Spacer( Modifier.height( 10.dp ) )

        Text(
            text = preview.playlist.name,
            style = LumaType.Tile,
            color = LumaColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$count ${if ( count == 1 ) "song" else "songs"}",
            style = LumaType.Meta,
            color = LumaColor.InkSoft,
            maxLines = 1
        )
    }
}

/**
 * A song, as a line rather than as a record card.
 *
 * No duration and no download button. Both were on every row of the old list, and both are
 * management detail: you consult a duration occasionally and you download deliberately, neither is
 * something you scan forty rows for. Long-press and the section screen still offer them.
 */
@Composable
private fun SongLine( song: Song, onPlay: () -> Unit ) = Row(
    Modifier
        .fillMaxWidth()
        .clickable( onClick = onPlay )
        .padding( horizontal = 24.dp, vertical = 8.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Artwork(
        title = song.cleanTitle(),
        thumbnailUrl = song.cleanThumbnailUrl(),
        modifier = Modifier
            .size( 52.dp )
            .clip( RoundedCornerShape( 10.dp ) )
    )

    Spacer( Modifier.width( 16.dp ) )

    Column {
        Text(
            text = song.cleanTitle(),
            style = LumaType.Tile,
            color = LumaColor.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song.cleanArtistsText(),
            style = LumaType.Meta,
            color = LumaColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Artwork with an initial as its fallback.
 *
 * Same reasoning as the Now screen: a missing cover is routine, and a letter on the surface colour
 * says "no art" while keeping a silhouette distinct enough to recognise on a second visit.
 */
@Composable
private fun Artwork(
    title: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    var failed by remember( thumbnailUrl ) { mutableStateOf( false ) }

    Box(
        modifier.background( LumaColor.Raised ),
        contentAlignment = Alignment.Center
    ) {
        if ( thumbnailUrl.isNullOrBlank() || failed )
            Text(
                text = title.trim().take( 1 ).uppercase(),
                style = LumaType.Title,
                color = LumaColor.InkSoft
            )
        else
            Image(
                painter = ImageFactory.rememberAsyncImagePainter(
                    thumbnailUrl = thumbnailUrl,
                    onError = { failed = true }
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
    }
}

@Composable
private fun EmptyLibrary( onOpenSearch: () -> Unit ) = Column(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 24.dp, vertical = 60.dp )
) {
    Text(
        text = "Nothing here yet",
        style = LumaType.Hero,
        color = LumaColor.Ink
    )

    Spacer( Modifier.height( 8.dp ) )

    Text(
        text = "Anything you play, follow or save shows up here.",
        style = LumaType.Meta,
        color = LumaColor.InkSoft
    )

    Spacer( Modifier.height( 20.dp ) )

    Text(
        text = "Find something",
        style = LumaType.Label,
        color = LumaColor.Ground,
        modifier = Modifier
            .clip( LumaShape.Pill )
            .background( LumaColor.Ember )
            .clickable( onClick = onOpenSearch )
            .padding( horizontal = 26.dp, vertical = 14.dp )
    )
}

/** How many items a shelf holds before you have to open the section to see the rest. */
private const val SHELF_LIMIT = 20

/** Songs shown inline before the "All N songs" way out. */
private const val SONG_PREVIEW = 6

// Tab indices understood by the section screen behind each shelf header.
private const val TAB_SONGS = 1
private const val TAB_ARTISTS = 2
private const val TAB_ALBUMS = 3
private const val TAB_PLAYLISTS = 4
