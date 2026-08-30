package app.kreate.android.themed.taste

import app.kreate.android.themed.luma.LumaRadius

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.taste.TasteEngine
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography

/**
 * "Your listening" — everything the app has learned, in plain language, with an undo beside it.
 *
 * This screen is the price of being allowed to personalise at all. An app that quietly reorders
 * what you hear owes you three things: to say what it noticed, to say what it did about it, and to
 * let you disagree in one tap. Everything here is phrased as an observation ("Skipped straight away
 * 4 times") rather than a verdict ("You dislike this"), because the observation is the part the app
 * actually knows.
 *
 * Nothing listed here has been hidden from you. Suppressed songs are still in your library and
 * still returned by search — they have only stopped being *suggested*.
 */
@Composable
fun TasteCentreScreen( modifier: Modifier = Modifier ) {

    var learningEnabled by Preferences.TASTE_LEARNING_ENABLED

    val suppressed by Database.listeningSignalTable
                              .suppressedSongs()
                              .collectAsState( initial = emptyList() )
    val loved by Database.listeningSignalTable
                         .lovedSongs()
                         .collectAsState( initial = emptyList() )

    var confirmingReset by remember { mutableStateOf( false ) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background( LumaColor.Ground )
            // Without this the title is drawn underneath the status bar — on a 1080x2400 phone
            // "Your listening" landed on top of the system clock (finding 31).
            .statusBarsPadding(),
        contentPadding = PaddingValues( bottom = 48.dp )
    ) {

        item {
            Column( Modifier.padding( 20.dp ) ) {
                Text(
                    text = "Your listening",
                    style = LumaType.Title,
                    color = LumaColor.Ink
                )
                Spacer( Modifier.height( 6.dp ) )
                Text(
                    text = "What the app has noticed, and what it did about it. " +
                           "Nothing here is hidden from your library or from search.",
                    style = LumaType.Tile,
                    color = LumaColor.InkSoft
                )
            }
        }

        item {
            SettingRow(
                title = "Learn from how I listen",
                subtitle = "Uses skips, replays and completions to order what gets suggested.",
                checked = learningEnabled,
                onCheckedChange = { learningEnabled = it }
            )
        }

        if ( suppressed.isNotEmpty() )
            item {
                SectionHeader(
                    title = "Stopped suggesting",
                    subtitle = "You skipped these straight away, more than once."
                )
            }

        items( suppressed, key = { "sup_${it.id}" } ) { song ->
            TasteRow(
                song = song,
                actionLabel = "I like this",
                // Restores it to suggestions *and* stops it being re-suppressed, since the user has
                // now said something the counters cannot override.
                onAction = { TasteEngine.overrideAsLiked( song.id ) },
                onForget = { TasteEngine.forget( song.id ) }
            )
        }

        if ( loved.isNotEmpty() )
            item {
                SectionHeader(
                    title = "Suggested more often",
                    subtitle = "You come back to these."
                )
            }

        items( loved, key = { "love_${it.id}" } ) { song ->
            TasteRow(
                song = song,
                actionLabel = null,
                onAction = {},
                onForget = { TasteEngine.forget( song.id ) }
            )
        }

        if ( suppressed.isEmpty() && loved.isEmpty() )
            item {
                Box(
                    Modifier.fillMaxWidth().padding( 32.dp ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing learned yet. Keep listening — it takes a few plays before " +
                               "the app is confident enough to change anything.",
                        style = LumaType.Tile,
                        color = LumaColor.InkSoft
                    )
                }
            }

        item {
            Spacer( Modifier.height( 24.dp ) )
            Box( Modifier.padding( horizontal = 20.dp ) ) {
                Box(
                    Modifier
                        .clip( RoundedCornerShape( LumaRadius.Card ) )
                        .background( LumaColor.Raised )
                        .clickable {
                            // Two taps, not a dialog. A modal for a reversible preference is
                            // heavier than the action deserves; a label change is enough of a
                            // speed bump to prevent a mis-tap.
                            if ( confirmingReset ) {
                                TasteEngine.forgetAll()
                                confirmingReset = false
                            } else confirmingReset = true
                        }
                        .padding( horizontal = 20.dp, vertical = 14.dp )
                ) {
                    Text(
                        text = if ( confirmingReset ) "Tap again to forget everything"
                               else "Reset everything the app has learned",
                        style = LumaType.Tile,
                        color = if ( confirmingReset ) LumaColor.Alarm else LumaColor.Ink
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader( title: String, subtitle: String ) =
    Column( Modifier.padding( start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp ) ) {
        Text(
            text = title,
            style = LumaType.Row,
            color = LumaColor.Ink
        )
        Text(
            text = subtitle,
            style = LumaType.Meta,
            color = LumaColor.InkSoft
        )
    }

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ( Boolean ) -> Unit
) = Row(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 20.dp, vertical = 12.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Column( Modifier.weight( 1f ) ) {
        Text( title, style = LumaType.Row, color = LumaColor.Ink )
        Text( subtitle, style = LumaType.Meta, color = LumaColor.InkSoft )
    }
    // The control needs its own gutter: with the text column running right up to the switch, the
    // last word of a wrapping description sits against it and reads as overlapping (finding 32).
    Spacer( Modifier.width( 16.dp ) )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors( checkedTrackColor = LumaColor.Ember )
    )
}

@Composable
private fun TasteRow(
    song: Song,
    actionLabel: String?,
    onAction: () -> Unit,
    onForget: () -> Unit
) = Row(
    Modifier
        .fillMaxWidth()
        .padding( horizontal = 20.dp, vertical = 8.dp ),
    verticalAlignment = Alignment.CenterVertically
) {
    Box(
        Modifier
            .size( 48.dp )
            .clip( RoundedCornerShape( LumaRadius.Panel ) )
            .background( LumaColor.Raised )
    ) {
        song.cleanThumbnailUrl()?.also {
            androidx.compose.foundation.Image(
                painter = ImageFactory.rememberAsyncImagePainter( thumbnailUrl = it ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    Spacer( Modifier.size( 12.dp ) )

    Column( Modifier.weight( 1f ) ) {
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

    if ( actionLabel != null ) {
        Spacer( Modifier.size( 8.dp ) )
        Box(
            Modifier
                .clip( RoundedCornerShape( LumaRadius.Sleeve ) )
                .background( LumaColor.Ember )
                .clickable( onClick = onAction )
                .padding( horizontal = 14.dp, vertical = 8.dp )
        ) {
            Text( actionLabel, style = LumaType.Meta, color = LumaColor.Ground )
        }
    }

    Spacer( Modifier.size( 8.dp ) )

    Box(
        Modifier
            .clip( RoundedCornerShape( LumaRadius.Sleeve ) )
            .background( LumaColor.Raised )
            .clickable( onClick = onForget )
            .padding( horizontal = 14.dp, vertical = 8.dp )
    ) {
        Text( "Forget", style = LumaType.Meta, color = LumaColor.InkSoft )
    }
}
