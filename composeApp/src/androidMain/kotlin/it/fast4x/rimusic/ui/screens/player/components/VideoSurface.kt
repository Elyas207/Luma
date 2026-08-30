package it.fast4x.rimusic.ui.screens.player.components

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player

/**
 * Renders the video track of whatever [player] is currently playing.
 *
 * This replaces the YouTube IFrame WebView, and the difference is structural rather than cosmetic.
 * The WebView was a second, independent player: it held its own copy of the video id captured in a
 * composition closure that never re-ran, so changing track left the previous video on screen; it
 * registered a lifecycle observer it never removed, leaking a WebView per open; and because
 * ExoPlayer was never `prepare()`d for video, position, duration, the scrubber, the notification
 * and the queue all drifted from what was on screen.
 *
 * Here the surface is only a view onto the player the rest of the app already owns. Track changes,
 * position, duration, buffering and errors are whatever the player says they are, because there is
 * nothing else to disagree with.
 */
@Composable
fun VideoSurface(
    player: Player,
    modifier: Modifier = Modifier
) {
    Box( modifier ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceView( context ).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            // Rebinding on every recomposition is what the old factory-only AndroidView failed to
            // do. Attaching a surface the player already holds is a no-op, so this is cheap.
            update = player::setVideoSurfaceView,
            // Detach before the view dies, otherwise the player keeps rendering into a dead
            // surface — the leak the WebView version never cleaned up either.
            onRelease = { player.clearVideoSurface() }
        )

        DisposableEffect( player ) {
            onDispose { player.clearVideoSurface() }
        }
    }
}
