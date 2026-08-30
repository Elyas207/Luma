package it.fast4x.rimusic.ui.screens.home

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.kreate.android.Preferences
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.semiBold

@Composable
fun MoodItemColored(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    val moodColor by remember { derivedStateOf { Color(mood.stripeColor) } }

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .clip(thumbnailRoundness.shape)
            .clickable { onClick() }

    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(color = moodColor)
                .padding(start = 10.dp)
                .fillMaxHeight(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .requiredWidth(150.dp)
                    .background(color = LumaColor.Raised)
                    .fillMaxSize()
            ) {

                BasicText(
                    text = mood.title,
                    style = TextStyle(
                        color = LumaColor.Ink,
                        fontStyle = LumaType.Meta.fontStyle,
                        fontWeight = LumaType.Meta.fontWeight
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp).align(Alignment.CenterStart),
                    maxLines = 2,

                    )
            }
        }
    }
}

@Composable
fun MoodGridItemColored(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSizeDp: Dp
) {
    var thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    val moodColor by remember { derivedStateOf { Color(mood.stripeColor) } }

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .size(thumbnailSizeDp,thumbnailSizeDp)
            .padding(5.dp)
            .clickable { onClick() }

    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .clip(thumbnailRoundness.shape)

        ) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(color = moodColor)
                    .padding(start = 10.dp)
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .background(color = LumaColor.Raised)
                        .fillMaxSize()
                ) {

                    BasicText(
                        text = mood.title,
                        style = TextStyle(
                            color = LumaColor.Ink,
                            fontStyle = LumaType.Meta.fontStyle,
                            fontWeight = LumaType.Meta.fontWeight
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp)
                            .align(Alignment.CenterStart),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
fun MoodItem(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(5.dp)
            .clip(thumbnailRoundness.shape)
            .clickable { onClick() }

    ) {
        Box(
            modifier = Modifier
                .requiredWidth(150.dp)
                .background(color = LumaColor.Raised, shape = thumbnailRoundness.shape)
                .fillMaxWidth(0.9f)
                .padding(all = 10.dp)
        ){

        BasicText(
            text = mood.title,
            style =  TextStyle(
                color = LumaColor.Ink,
                fontStyle = LumaType.Meta.fontStyle,
                fontWeight = LumaType.Meta.fontWeight
            ), //LumaType.Meta,
            modifier = Modifier.padding(start = 4.dp),
            maxLines = 1,

        )
        }
    }
}

@Composable
fun MoodGridItem(
    mood: Innertube.Mood.Item,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSizeDp: Dp
) {
    var thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .size(thumbnailSizeDp,thumbnailSizeDp)
            //.background(LumaColor.Raised)
            .clip(thumbnailRoundness.shape)
            .clickable { onClick() }

    ) {
        Box(
            modifier = Modifier
                .background(color = LumaColor.Raised, shape = thumbnailRoundness.shape)
                .fillMaxSize(0.9f)
                .padding(horizontal = 10.dp)
                .padding(vertical = 50.dp)
        ) {
            BasicText(
                text = mood.title,
                style = TextStyle(
                    color = LumaColor.Ink,
                    fontStyle = LumaType.Title.fontStyle,
                    fontWeight = LumaType.Title.fontWeight,
                    fontFamily = LumaType.Title.fontFamily,
                    textAlign = TextAlign.Start
                ),
                modifier = modifier.padding(start = 4.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun MoodItemPlaceholder(
    width: Dp,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier
            .background(color = LumaColor.Raised)
            .size(width, 64.dp)
    )
}
