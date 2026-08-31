package it.fast4x.rimusic.enums

import app.kreate.android.themed.luma.LumaRadius

import androidx.annotation.StringRes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class ThumbnailRoundness(
    val shape: Shape,
    @field:StringRes override val androidTextId: Int
): TextView {

    None( RoundedCornerShape(0.dp), R.string.none ),

    Light( RoundedCornerShape( LumaRadius.Panel ), R.string.light ),

    Medium( RoundedCornerShape( LumaRadius.Panel ), R.string.medium ),

    Heavy( RoundedCornerShape( LumaRadius.Card ), R.string.heavy );
}
