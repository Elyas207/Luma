package app.kreate.android.themed.now

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.android.themed.luma.LumaArchTile
import app.kreate.android.themed.luma.LumaArtwork
import app.kreate.android.themed.luma.LumaAtmosphere
import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaLabel
import app.kreate.android.themed.luma.LumaRingButton
import app.kreate.android.themed.luma.LumaShape
import app.kreate.android.themed.luma.LumaType
import app.kreate.android.themed.luma.rememberArtworkAccent
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import java.util.Calendar

/**
 * Luma's home.
 *
 * Two problems, and they are different problems.
 *
 * **The first is what the screen is for.** It used to be a filing cabinet — taxonomy tabs above a
 * title, a toolbar and a row of chips, roughly a quarter of the display spent on furniture before
 * any music appeared. It answered "where is my stuff filed", which is not why anyone opens a music
 * app; about seven opens in ten are "put something on", and the user already knows roughly what.
 * That was fixed in an earlier pass by leading with a resume hero.
 *
 * **The second is what the screen looks like, and that was not fixed.** Leading with a hero and a
 * horizontal shelf of rounded squares is precisely what Spotify, Apple Music, Deezer and YouTube
 * Music all do — pulled up side by side on Mobbin their home screens are genuinely difficult to tell
 * apart, because the silhouette is identical even though every colour and typeface differs. Changing
 * the finish on that layout produces a fifth member of the set.
 *
 * So the shapes change:
 *
 * - **Everything you can pick is an arch** — a semicircular top on a squared base. It reads as a
 *   window you are looking through rather than a card you are tapping, it is the rectangle's
 *   opposite at identical cost, and no mainstream media app uses it. The player answers with a
 *   *disc*, so the two screens share a language without wearing the same shape.
 * - **The section heading is oversized display serif and the artwork overlaps it**, instead of a
 *   small bold sans label sitting obediently above a row. Type becomes texture.
 * - **The screen is lit by whatever you were last listening to**, via the same artwork-derived
 *   emanation the player uses.
 *
 * Navigation depth is unchanged — the library is still one tap — it has simply stopped being the
 * first thing you are made to read.
 */
@Composable
fun NowScreen(
    onPlaySong: ( Song ) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenDestination: ( it.fast4x.rimusic.enums.NavRoutes ) -> Unit,
    modifier: Modifier = Modifier
) {
    val recents by Database.eventTable
                           .recentlyPlayed( 20 )
                           .collectAsState( initial = emptyList() )
    val loved by Database.listeningSignalTable
                         .lovedSongs()
                         .collectAsState( initial = emptyList() )

    // Where playback actually was, which is not the same as the last thing that finished. An app
    // the OS kills mid-track leaves no history row at all, so without this the screen greets a
    // listener who was 40 minutes into a recitation with "Nothing playing yet".
    val inProgress by Database.queueTable
                              .inProgress()
                              .collectAsState( initial = null )

    val hero = inProgress?.song ?: recents.firstOrNull()

    // Loved first, then merely recent, with the hero removed so the screen never offers the same
    // track twice.
    val shelf = remember( recents, loved, hero ) {
        ( loved + recents ).distinctBy( Song::id ).filter { it.id != hero?.id }.take( 12 )
    }

    val accent by rememberArtworkAccent( hero?.cleanThumbnailUrl() )

    // 720dp is where a single column stops being the honest shape.
    val isWide = LocalConfiguration.current.screenWidthDp >= 720

    Box( modifier.fillMaxSize().background( LumaColor.Ground ) ) {

        // Eased from 0.75. The atmosphere is decorative — it takes one colour from the artwork and
        // lights the room with it — but it sits *behind text*, and at 0.75 it eroded the masthead
        // greeting to 4.02:1 against the 4.5:1 an 11sp label needs. Decoration loses to legibility.
        LumaAtmosphere( accent, Modifier.fillMaxSize(), intensity = 0.5f )

        Box(
            Modifier
                .fillMaxSize()
                // No app bar on this screen, so nothing else reserves room for the system bars.
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            if ( isWide )
                WideNow( hero, shelf, accent, onPlaySong, onOpenLibrary, onOpenSearch, onOpenCarMode, onOpenDestination )
            else
                NarrowNow( hero, shelf, accent, onPlaySong, onOpenLibrary, onOpenSearch, onOpenCarMode, onOpenDestination )
        }
    }
}

/** Phone: one column, the arch hero owning most of the first screenful. */
@Composable
private fun NarrowNow(
    hero: Song?,
    shelf: List<Song>,
    accent: Color,
    onPlaySong: ( Song ) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenDestination: ( it.fast4x.rimusic.enums.NavRoutes ) -> Unit
) = LazyColumn( contentPadding = PaddingValues( bottom = 132.dp ) ) {

    item { Masthead( onOpenSearch, onOpenDestination ) }

    item {
        if ( hero != null )
            HeroArch(
                hero, accent,
                Modifier
                    .fillMaxWidth()
                    .padding( horizontal = 24.dp )
                    // A new library often has one recent track and nothing loved, so there is no
                    // shelf to follow the hero. At a fixed ratio that left the bottom third of the
                    // screen as dead black; with nothing to scroll to, the hero should simply be
                    // the screen.
                    .aspectRatio( if ( shelf.isEmpty() ) 0.66f else 0.82f )
            ) { onPlaySong( hero ) }
        else
            FirstRun( onOpenSearch )
    }

    if ( shelf.isNotEmpty() )
        item {
            Spacer( Modifier.height( 44.dp ) )
            OverlappedHeading( "Back to" )
            LazyRow(
                contentPadding = PaddingValues( horizontal = 24.dp ),
                horizontalArrangement = Arrangement.spacedBy( 14.dp )
            ) {
                items( shelf, key = Song::id ) { song ->
                    LumaArchTile(
                        thumbnailUrl = song.cleanThumbnailUrl(),
                        title = song.cleanTitle(),
                        subtitle = song.cleanArtistsText(),
                        accent = accent,
                        onClick = { onPlaySong( song ) },
                        modifier = Modifier
                            .width( 156.dp )
                            .height( 216.dp )
                    )
                }
            }
        }

    item { QuietLinks( onOpenLibrary, onOpenSearch, onOpenCarMode ) }
}

/**
 * Tablet and car display: hero and shelf side by side.
 *
 * A landscape tablet is roughly two phones wide and *no taller*, so stacking hero above shelf spends
 * the extra pixels on nothing and hides the shelf below the fold anyway. Beside each other, the whole
 * screen is a single glance — which matters most in a car, where a scroll is a second look away from
 * the road.
 */
@Composable
private fun WideNow(
    hero: Song?,
    shelf: List<Song>,
    accent: Color,
    onPlaySong: ( Song ) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCarMode: () -> Unit,
    onOpenDestination: ( it.fast4x.rimusic.enums.NavRoutes ) -> Unit
) = Row( Modifier.fillMaxSize() ) {

    // With no history there is no shelf, so the split would leave half a tablet as dead black —
    // the "empty space with no purpose" failure, at its most conspicuous on the largest screen.
    // One centred column instead, which is also the only time this screen has a single message.
    val split = hero != null && shelf.isNotEmpty()

    Column( Modifier.weight( if ( split ) 0.52f else 1f ) ) {
        Masthead( onOpenSearch, onOpenDestination )

        if ( hero != null )
            HeroArch(
                hero, accent,
                Modifier
                    .fillMaxWidth()
                    .weight( 1f )
                    .padding( horizontal = 28.dp )
            ) { onPlaySong( hero ) }
        else
            Box(
                Modifier.fillMaxWidth().weight( 1f ),
                contentAlignment = Alignment.Center
            ) { FirstRun( onOpenSearch ) }

        QuietLinks( onOpenLibrary, onOpenSearch, onOpenCarMode )
    }

    if ( shelf.isNotEmpty() )
        Column( Modifier.weight( 0.48f ) ) {
            Spacer( Modifier.height( 30.dp ) )
            OverlappedHeading( "Back to" )

            // Two columns of arches rather than one column of rows: a 48%-wide column fits exactly
            // one full-width row per line, and a grid of one is a list wearing a costume.
            LazyColumn(
                contentPadding = PaddingValues( start = 8.dp, end = 28.dp, bottom = 120.dp ),
                verticalArrangement = Arrangement.spacedBy( 14.dp )
            ) {
                items( shelf.chunked( 2 ), key = { it.first().id } ) { pair ->
                    Row( horizontalArrangement = Arrangement.spacedBy( 14.dp ) ) {
                        pair.forEach { song ->
                            LumaArchTile(
                                thumbnailUrl = song.cleanThumbnailUrl(),
                                title = song.cleanTitle(),
                                subtitle = song.cleanArtistsText(),
                                accent = accent,
                                onClick = { onPlaySong( song ) },
                                modifier = Modifier
                                    .weight( 1f )
                                    .aspectRatio( 0.74f )
                            )
                        }
                        // Keeps a trailing odd tile at column width instead of letting it stretch
                        // across the full row, which would break the grid's rhythm on the last line.
                        if ( pair.size == 1 ) Spacer( Modifier.weight( 1f ) )
                    }
                }
            }
        }
}

/**
 * The wordmark, set in the app's own display serif, and one control.
 *
 * "Luma" as *text* rather than artwork so it inherits the palette and stays legible on every skin —
 * the old fixed-colour vector could not. Search is a ring rather than a bar: a permanent search
 * field is a promise that search is the primary action, and here it is the second.
 */
@Composable
private fun Masthead(
    onOpenSearch: () -> Unit,
    onOpenDestination: ( it.fast4x.rimusic.enums.NavRoutes ) -> Unit
) {

    val showDestinations = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf( false ) }

    val hour = remember { Calendar.getInstance().get( Calendar.HOUR_OF_DAY ) }
    val greeting = when ( hour ) {
        in 5..11  -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else      -> "Late one"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding( start = 24.dp, end = 20.dp, top = 22.dp, bottom = 26.dp ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column( Modifier.weight( 1f ) ) {
            Text(
                text = "Luma",
                style = LumaType.Section,
                color = LumaColor.Ink
            )
            Spacer( Modifier.height( 3.dp ) )
            // The time of day is the single most reliable context signal a media app has, and
            // acknowledging it costs one line. Deliberately the smallest text on screen — a greeting
            // that shouts is a greeting you resent by the third day.
            LumaLabel( greeting )
        }

        LumaRingButton(
            iconRes = app.kreate.android.R.drawable.search,
            contentDescription = "Search",
            onClick = onOpenSearch,
            diameter = 46.dp
        )

        Spacer( Modifier.width( 10.dp ) )

        // Everything that is not playback: history, statistics, appearance, what the app has
        // learned, handoff, car mode, settings. These previously existed *only* behind the
        // overflow on the search-results screen, so the way to reach Settings was to search for
        // something first (finding 5). Same menu, not a copy of it.
        LumaRingButton(
            iconRes = app.kreate.android.R.drawable.ellipsis_vertical,
            contentDescription = "More",
            onClick = { showDestinations.value = true },
            diameter = 46.dp
        )
    }

    it.fast4x.rimusic.ui.components.navigation.header.HamburgerMenu(
        expanded = showDestinations.value,
        onItemClick = {
            showDestinations.value = false
            onOpenDestination( it )
        },
        onDismissRequest = { showDestinations.value = false }
    )
}

/**
 * The one thing you probably came for, as an arch.
 *
 * Its own artwork fills it, so the screen takes its colour from what you were actually listening to
 * rather than from a fixed brand palette. The scrim is non-negotiable and weighted low: artwork
 * brightness is arbitrary, text legibility is not, and darkening the top would fight the curve that
 * gives the shape its whole point.
 */
@Composable
private fun HeroArch(
    song: Song,
    accent: Color,
    modifier: Modifier,
    onPlay: () -> Unit
) = Box(
    modifier
        .clip( LumaShape.Arch )
        .clickable( onClick = onPlay )
) {
    LumaArtwork( song.cleanThumbnailUrl(), song.cleanTitle(), Modifier.fillMaxSize(), accent )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                // Scrim to the *palette's* ground, not to black. The text over this hero is
                // `LumaColor.Ink`, which is near-white on a dark skin and near-black on a light
                // one — so a fixed black scrim reads correctly on Obsidian and puts dark navy text
                // on a near-black base under Aurora, which is the "can't see anything in Frutiger
                // Aero" report. Fading to Ground keeps text and its backing on the same side.
                // The ramp starts earlier and climbs harder than it used to. On a phone the label
                // sits low enough that a 42% midpoint was enough; on a tablet the hero is far
                // taller, the text block sits nearer the middle of the artwork, and against a
                // bright sunset "PICK UP WHERE YOU LEFT OFF" measured 1.97:1 — well under the 3:1
                // large text needs. Measured, not guessed: the contrast oracle on emulator-5554.
                Brush.verticalGradient(
                    0.00f to LumaColor.Ground.copy( alpha = 0f ),
                    0.30f to LumaColor.Ground.copy( alpha = 0.45f ),
                    0.55f to LumaColor.Ground.copy( alpha = 0.82f ),
                    1.00f to LumaColor.Ground.copy( alpha = 0.96f )
                )
            )
    )

    Column(
        Modifier
            .align( Alignment.BottomStart )
            .fillMaxWidth()
            // The text sits on a near-solid plate rather than on the photograph.
            //
            // A gradient cannot carry small text over arbitrary artwork: the hero shows whatever
            // the user last played, so the pixels behind this line are unknowable at design time.
            // Against a bright sunset on a tablet — where the hero is tall enough that this label
            // sits near the middle of the image — it measured 1.97:1, and strengthening the ramp
            // only reached 2.99:1 against the 4.5:1 an 11sp label needs. This is the theme brief's
            // own rule: if a design needs text over an image, the design changes.
            .background(
                Brush.verticalGradient(
                    0f to LumaColor.Ground.copy( alpha = 0.86f ),
                    1f to LumaColor.Ground.copy( alpha = 0.97f )
                )
            )
            .padding( start = 24.dp, end = 24.dp, top = 18.dp, bottom = 26.dp )
    ) {
        LumaLabel( "Pick up where you left off", color = LumaColor.InkSoft )

        Spacer( Modifier.height( 10.dp ) )

        Text(
            text = song.cleanTitle(),
            style = LumaType.Hero,
            color = LumaColor.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer( Modifier.height( 6.dp ) )

        Text(
            text = song.cleanArtistsText(),
            style = LumaType.Meta,
            color = LumaColor.InkSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer( Modifier.height( 20.dp ) )

        LumaRingButton(
            iconRes = app.kreate.android.R.drawable.play,
            contentDescription = "Play",
            onClick = onPlay,
            diameter = 62.dp,
            filled = true,
            accent = accent
        )
    }
}

/**
 * A section heading big enough to be a texture.
 *
 * Set at display size and allowed to sit tight against the row beneath it so the artwork crowds it,
 * rather than a small bold label with obedient clearance above a shelf. This is lifted from editorial
 * layout — and from Airbuds Widget, which is the one music app on Mobbin whose home screen does not
 * look like the other four.
 */
@Composable
private fun OverlappedHeading( text: String ) = Text(
    text = text,
    style = LumaType.Hero,
    color = LumaColor.Ink.copy( alpha = 0.92f ),
    modifier = Modifier.padding( start = 24.dp, bottom = 2.dp )
)

/**
 * Everything else, as words.
 *
 * The library has not moved; it has stopped being the first thing you must read. Text rather than a
 * tab strip because these are destinations visited occasionally and on purpose, and a permanent bar
 * for an occasional action is how the old screen ended up nearly a quarter chrome.
 */
@Composable
private fun QuietLinks(
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCarMode: () -> Unit
) = Row(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 24.dp, vertical = 34.dp ),
    horizontalArrangement = Arrangement.spacedBy( 26.dp )
) {
    // Car mode belongs here, not only behind the old app bar's overflow. This screen deliberately
    // has no app bar, and the car display is the device this fork exists for — leaving its only
    // entrance on a surface the redesign removed would have stranded it.
    listOf(
        "Your library" to onOpenLibrary,
        "Search" to onOpenSearch,
        "Car mode" to onOpenCarMode
    ).forEach { ( label, action ) ->
        Text(
            text = label,
            style = LumaType.Row,
            color = LumaColor.InkSoft,
            modifier = Modifier
                .clip( CircleShape )
                .clickable( onClick = action )
                .padding( vertical = 4.dp )
        )
    }
}

/**
 * First run: no history to lead with.
 *
 * States the one thing worth doing rather than showing a grid of empty placeholders, which is how
 * most apps make a fresh install feel broken.
 */
@Composable
private fun FirstRun( onOpenSearch: () -> Unit ) = Column(
    Modifier
        .fillMaxWidth()
        // ~62 characters is the readable measure; uncapped on a 1280dp tablet a paragraph becomes
        // genuinely hard to track from line to line.
        .widthIn( max = 560.dp )
        .padding( horizontal = 24.dp, vertical = 40.dp )
) {
    Text(
        text = "Nothing playing yet.",
        style = LumaType.Hero,
        color = LumaColor.Ink
    )
    Spacer( Modifier.height( 12.dp ) )
    Text(
        text = "Find something once and this screen becomes yours — it leads with whatever you " +
               "actually come back to.",
        style = LumaType.Meta,
        color = LumaColor.InkSoft
    )
    Spacer( Modifier.height( 26.dp ) )
    Row( verticalAlignment = Alignment.CenterVertically ) {
        LumaRingButton(
            iconRes = app.kreate.android.R.drawable.search,
            contentDescription = "Find something",
            onClick = onOpenSearch,
            diameter = 56.dp,
            filled = true,
            accent = LumaColor.Ember
        )
        Spacer( Modifier.width( 16.dp ) )
        Text(
            text = "Find something",
            style = LumaType.Row,
            color = LumaColor.Ink,
            modifier = Modifier.clickable( onClick = onOpenSearch )
        )
    }
}
