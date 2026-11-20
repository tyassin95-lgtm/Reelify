package app.reelify.feature.common

import android.content.Context
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps a Media3 CompositionPlayer so preview plays the very same Composition (effects, overlays,
 * audio processors) that export renders.
 */
class PreviewController(context: Context) {
    val player: CompositionPlayer = CompositionPlayer.Builder(context.applicationContext).build()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    var durationMs: Long = 0; private set

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _playing.value = isPlaying }
            override fun onPlayerError(error: PlaybackException) { _error.value = "Preview error: ${error.errorCodeName}" }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) { _ready.value = true; durationMs = player.duration.coerceAtLeast(0) } }
        })
    }

    fun set(composition: Composition, positionMs: Long = 0, play: Boolean = false) {
        _error.value = null
        _ready.value = false
        player.setComposition(composition, positionMs.coerceAtLeast(0))
        player.prepare()
        player.playWhenReady = play
    }

    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() } }
    fun seek(ms: Long) = player.seekTo(ms.coerceAtLeast(0))
    fun position(): Long = player.currentPosition
    fun pause() = player.pause()
    fun release() = player.release()
}

@Composable
fun PlayerSurface(controller: PreviewController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = controller.player
            }
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}

@Composable
fun rememberPreviewController(context: Context): PreviewController {
    val c = androidx.compose.runtime.remember { PreviewController(context) }
    DisposableEffect(Unit) { onDispose { c.release() } }
    return c
}
