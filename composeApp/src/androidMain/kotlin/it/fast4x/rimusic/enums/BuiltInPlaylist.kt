package it.fast4x.rimusic.enums

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.kreate.android.R
import me.knighthat.enums.TextView

enum class BuiltInPlaylist(
    @field:DrawableRes override val androidIconId: Int,
    @field:StringRes override val androidTextId: Int
): Drawable, TextView {

    // Labelled "All", not "Songs". These chips filter *within* the songs section, and the
    // section rail above already says "Songs" — the old label made the same word appear twice on
    // one screen for two different jobs.
    All( R.drawable.musical_notes, R.string.all ),

    Favorites( R.drawable.heart, R.string.favorites ),

    Offline( R.drawable.sync, R.string.cached ),

    Downloaded( R.drawable.downloaded, R.string.downloaded ),

    Top( R.drawable.trending, R.string.playlist_top ),

    OnDevice( R.drawable.musical_notes, R.string.on_device )
}
