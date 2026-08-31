package it.fast4x.rimusic.utils

import app.kreate.android.themed.luma.LumaColor
import app.kreate.android.themed.luma.LumaType
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import it.fast4x.rimusic.typography
import java.io.File

@Composable
fun ShowUpdatedVersion(
    updatedVersion: String,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    //val file = getFilesDir() //shows as unresolved reference
    val file = File(LocalContext.current.filesDir, "RiMusicUpdatedVersion.ver")
    val newVersion = file.readText()

    BasicText(
        text = "New version $newVersion",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = LumaType.Meta.secondary,
    )
}