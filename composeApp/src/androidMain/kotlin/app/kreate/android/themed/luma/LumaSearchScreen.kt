package app.kreate.android.themed.luma

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import app.kreate.database.models.SearchQuery
import it.fast4x.rimusic.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchSuggestionsBody
import it.fast4x.innertube.requests.searchSuggestionsWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Search.
 *
 * The screen this replaces was three tabs (Online / Library / Go to link) under the app's shared
 * chrome — a logo, a hamburger, an app bar, a nav rail — wrapped around a thin text field. Roughly
 * a third of the display was furniture, the three tabs were a technical distinction rather than a
 * user one (nobody arrives wanting to search "the library" specifically; they want the thing), and
 * a "go to link" tab occupied a permanent third of the switcher for an action performed
 * approximately never.
 *
 * The rewrite keeps every data source the old screen used — the same history table, the same
 * `searchSuggestionsWithItems` call — and changes what surrounds them:
 *
 * - **No chrome at all.** No app bar, no logo, no tab strip. The field is the screen, because
 *   searching is a mode you enter and leave, not a place you furnish.
 * - **Suggestions are set in the display serif**, spaced like an index rather than crammed like a
 *   dropdown. This is the one screen where the user reads a list of pure text, so it is the one
 *   screen where the typeface has to carry all of the character on its own.
 * - **History and suggestions are one list, not two panes.** They answer the same question, and the
 *   old screen's split meant your own recent search could be below the fold while a remote
 *   suggestion sat above it.
 *
 * Link handling has not been deleted — a pasted URL is still a query, and the results screen
 * resolves it. It has simply stopped costing a third of a tab bar.
 */
@Composable
fun LumaSearchScreen(
    initialTextInput: String,
    onSearch: ( String ) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var field by remember {
        mutableStateOf(
            TextFieldValue( initialTextInput, TextRange( initialTextInput.length ) )
        )
    }

    val history by remember( field.text ) {
        Database.searchTable
                .findAllContain( field.text )
                .distinctUntilChanged()
                .map { it.reversed() }
    }.collectAsState( emptyList(), Dispatchers.IO )

    var suggestions by remember { mutableStateOf<List<String>>( emptyList() ) }

    LaunchedEffect( field.text ) {
        if ( field.text.isBlank() ) {
            suggestions = emptyList()
            return@LaunchedEffect
        }

        // Debounced so a fast typist does not fire a request per keystroke.
        delay( 200 )
        suggestions = Innertube.searchSuggestionsWithItems(
                                   SearchSuggestionsBody( input = field.text )
                               )
                               ?.getOrNull()
                               ?.queries
                               .orEmpty()
    }

    // Opening search and then having to tap the field is the single most common small annoyance in
    // a search screen; the user's intent was unambiguous the moment they got here.
    val focus = remember { FocusRequester() }
    LaunchedEffect( Unit ) { focus.requestFocus() }

    val submit = {
        val query = field.text.trim()
        if ( query.isNotEmpty() ) onSearch( query )
    }

    // Search has no artwork of its own to take light from, so it keeps the app's own ember. It is
    // the one screen that should feel like a neutral room.
    Box( modifier.fillMaxSize().background( LumaColor.Ground ) ) {

        LumaAtmosphere( LumaColor.Ember, Modifier.fillMaxSize(), intensity = 0.30f )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding( start = 24.dp, end = 20.dp, top = 18.dp, bottom = 6.dp ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    style = LumaType.Hero,
                    color = LumaColor.Ink,
                    modifier = Modifier.weight( 1f )
                )

                LumaRingButton(
                    iconRes = R.drawable.chevron_down,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    diameter = 46.dp
                )
            }

            QueryField( field, focus, { field = it }, submit )

            LazyColumn(
                contentPadding = PaddingValues( top = 18.dp, bottom = 120.dp )
            ) {
                if ( history.isNotEmpty() )
                    item {
                        LumaLabel(
                            "Recent",
                            Modifier.padding( start = 26.dp, top = 6.dp, bottom = 10.dp )
                        )
                    }

                items( history, key = { "h:" + it.query } ) { entry ->
                    SuggestionLine( entry.query, recent = true ) {
                        field = TextFieldValue( entry.query, TextRange( entry.query.length ) )
                        onSearch( entry.query )
                    }
                }

                if ( suggestions.isNotEmpty() )
                    item {
                        LumaLabel(
                            "Suggestions",
                            Modifier.padding( start = 26.dp, top = 22.dp, bottom = 10.dp )
                        )
                    }

                items( suggestions, key = { "s:$it" } ) { query ->
                    SuggestionLine( query, recent = false ) { onSearch( query ) }
                }

                if ( history.isEmpty() && suggestions.isEmpty() )
                    item { Prompt() }
            }
        }
    }
}

/**
 * The field.
 *
 * A hairline under the text rather than a filled capsule. A filled input is a *form* control — it
 * says "one of several things to complete" — and here it is the only thing on the screen, so it
 * needs no container to be found. The rule also lets the caret sit on the same baseline logic as
 * the serif above it, which a pill's vertical centring does not.
 */
@Composable
private fun QueryField(
    value: TextFieldValue,
    focus: FocusRequester,
    onValueChange: ( TextFieldValue ) -> Unit,
    onSubmit: () -> Unit
) = Column( Modifier.padding( horizontal = 24.dp ) ) {

    Box( contentAlignment = Alignment.CenterStart ) {

        if ( value.text.isEmpty() )
            Text(
                text = "What are you listening for?",
                style = LumaType.Row,
                color = LumaColor.InkFaint
            )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LumaType.Row.copy( color = LumaColor.Ink ),
            cursorBrush = SolidColor( LumaColor.Ember ),
            keyboardOptions = KeyboardOptions( imeAction = ImeAction.Search ),
            keyboardActions = KeyboardActions( onSearch = { onSubmit() } ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester( focus )
        )
    }

    Spacer( Modifier.height( 12.dp ) )

    Box(
        Modifier
            .fillMaxWidth()
            .height( 1.dp )
            .background( LumaColor.Ink.copy( alpha = 0.18f ) )
    )
}

/**
 * One suggestion.
 *
 * Serif, generously spaced, no leading icon. The old screen put a magnifier glyph on every row,
 * which is a fourteen-identical-icons problem in miniature: if every item carries the same mark, the
 * mark carries no information and only costs the text its left margin. Recency is shown by weight
 * instead — the things you have searched before are brighter than the ones YouTube is guessing.
 */
@Composable
private fun SuggestionLine(
    query: String,
    recent: Boolean,
    onClick: () -> Unit
) = Text(
    text = query,
    style = LumaType.Row,
    color = if ( recent ) LumaColor.Ink else LumaColor.InkSoft,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier
        .fillMaxWidth()
        .clickable( onClick = onClick )
        .padding( horizontal = 26.dp, vertical = 14.dp )
)

/** Nothing typed and nothing remembered — say what this is for rather than showing a blank pane. */
@Composable
private fun Prompt() = Column(
    Modifier
        .fillMaxWidth()
        .widthIn( max = 520.dp )
        .padding( horizontal = 26.dp, vertical = 40.dp ),
    verticalArrangement = Arrangement.Center
) {
    Text(
        text = "Anything you can name.",
        style = LumaType.Section,
        color = LumaColor.InkSoft
    )
    Spacer( Modifier.height( 8.dp ) )
    Text(
        text = "A track, a reciter, an album — or paste a link.",
        style = LumaType.Meta,
        color = LumaColor.InkFaint
    )
}
