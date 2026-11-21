package app.reelify.feature.common

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Reliable single-clip player for TrimScreen using standard Media3 [ExoPlayer].
 * Falls back automatically to unfiltered playback if GPU effects fail on the device,
 * and never crashes on bad media or player lifecycle transitions.
 */
class TrimPlayerController(context: Context) {
    private val appContext = context.applicationContext
    val player: ExoPlayer = ExoPlayer.Builder(appContext).build()

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    var durationMs: Long = 0L
        private set

    private var lastUri: Uri? = null
    private var lastStartMs: Long = 0L
    private var lastVolume: Float = 1f
    private var activeEffects: List<Effect> = emptyList()
    private var effectsSupported: Boolean = true
    private var released: Boolean = false

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Trim ExoPlayer error code=${error.errorCodeName}", error)
                if (activeEffects.isNotEmpty() && effectsSupported) {
                    // Retry cleanly without GPU effects so trimming never breaks.
                    effectsSupported = false
                    activeEffects = emptyList()
                    _notice.value = "Live filter preview isn't supported for this clip; trimming still works and beauty applies at render."
                    lastUri?.let { uri ->
                        loadInternal(uri, player.currentPosition.coerceAtLeast(lastStartMs), lastVolume, emptyList(), play = false)
                    }
                    return
                }
                _error.value = "Couldn't play this clip (${error.errorCodeName}). You can replace it or return safely."
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _ready.value = true
                    durationMs = player.duration.takeIf { it > 0 } ?: 0L
                }
            }
        })
    }

    fun load(
        uri: Uri,
        startPositionMs: Long = 0L,
        volume: Float = 1f,
        effects: List<Effect> = emptyList(),
        play: Boolean = false,
    ) {
        if (released) return
        lastUri = uri
        lastStartMs = startPositionMs.coerceAtLeast(0L)
        lastVolume = volume.coerceIn(0f, 1f)
        val useEffects = if (effectsSupported) effects else emptyList()
        loadInternal(uri, lastStartMs, lastVolume, useEffects, play)
    }

    private fun loadInternal(
        uri: Uri,
        startPositionMs: Long,
        volume: Float,
        effects: List<Effect>,
        play: Boolean,
    ) {
        _error.value = null
        _ready.value = false
        activeEffects = effects
        runCatching {
            if (effects.isNotEmpty()) {
                runCatching { player.setVideoEffects(effects) }
                    .onFailure { t ->
                        Log.w(TAG, "Video effects unsupported in trim ExoPlayer; falling back to unfiltered preview", t)
                        effectsSupported = false
                        activeEffects = emptyList()
                        _notice.value = "Live filter preview isn't supported for this clip; trimming still works."
                        runCatching { player.setVideoEffects(emptyList()) }
                    }
            } else {
                runCatching { player.setVideoEffects(emptyList()) }
            }
            player.volume = volume.coerceIn(0f, 1f)
            player.setMediaItem(MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = play
        }.onFailure { t ->
            Log.e(TAG, "Failed to load clip in TrimPlayerController", t)
            _error.value = "Couldn't open this clip for trimming (${t.message ?: t.javaClass.simpleName})."
        }
    }

    fun setVolume(volume: Float) {
        lastVolume = volume.coerceIn(0f, 1f)
        if (!released) runCatching { player.volume = lastVolume }
    }

    fun toggle() {
        if (released) return
        runCatching {
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(lastStartMs)
                player.play()
            }
        }.onFailure { Log.e(TAG, "Toggle failed", it) }
    }

    fun seek(ms: Long) {
        if (released) return
        runCatching { player.seekTo(ms.coerceAtLeast(0L)) }.onFailure { Log.e(TAG, "Seek failed", it) }
    }

    fun position(): Long = if (released) 0L else runCatching { player.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)

    fun pause() {
        if (!released) runCatching { player.pause() }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { player.release() }.onFailure { Log.w(TAG, "Error releasing trim ExoPlayer", it) }
    }

    companion object {
        private const val TAG = "ReelifyTrim"
    }
}

@Composable
fun TrimPlayerSurface(controller: TrimPlayerController, modifier: Modifier = Modifier) {
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
        update = { view ->
            if (view.player !== controller.player) view.player = controller.player
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}

@Composable
fun rememberTrimPlayerController(context: Context): TrimPlayerController {
    val controller = remember { TrimPlayerController(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                controller.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    return controller
}

/**
 * Preview controller backed primarily by standard [ExoPlayer] for rendered proxy/exported MP4
 * playback (`setFile`), with a fully guarded [CompositionPlayer] fallback (`set`) so neither
 * Trim, Preview, nor the Timeline Editor can crash the app on playback setup or unsupported devices.
 */
class PreviewController(context: Context) {
    private val appContext = context.applicationContext
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(appContext).build()
    private var compositionPlayer: CompositionPlayer? = null
    private val _activePlayer = MutableStateFlow<Player>(exoPlayer)
    val activePlayer: StateFlow<Player> = _activePlayer.asStateFlow()

    val player: Player get() = _activePlayer.value

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()
    var durationMs: Long = 0L
        private set
    private var released = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Preview playback error code=${error.errorCodeName}", error)
            _error.value = "Preview is unavailable (${error.errorCodeName}). Open the rendered video."
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _ready.value = true
                durationMs = _activePlayer.value.duration.coerceAtLeast(0L)
            }
        }
    }

    init {
        exoPlayer.addListener(listener)
    }

    fun setFile(file: File, positionMs: Long = 0L, play: Boolean = false) {
        if (released) return
        _error.value = null
        _ready.value = false
        if (!file.exists() || file.length() <= 0L) {
            _error.value = "Preview is unavailable. Open the rendered video."
            return
        }
        runCatching {
            compositionPlayer?.pause()
            _activePlayer.value = exoPlayer
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), positionMs.coerceAtLeast(0L))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = play
        }.onFailure { t ->
            Log.e(TAG, "Failed to play rendered preview file ${file.name}", t)
            _error.value = "Preview is unavailable. Open the rendered video."
        }
    }

    fun set(composition: Composition, positionMs: Long = 0L, play: Boolean = false) {
        if (released) return
        _error.value = null
        _ready.value = false
        runCatching {
            exoPlayer.pause()
            val cp = compositionPlayer ?: CompositionPlayer.Builder(appContext).build().also {
                it.addListener(listener)
                compositionPlayer = it
            }
            _activePlayer.value = cp
            cp.setComposition(composition, positionMs.coerceAtLeast(0L))
            cp.prepare()
            cp.playWhenReady = play
        }.onFailure { t ->
            Log.e(TAG, "CompositionPlayer preview setup failed", t)
            _activePlayer.value = exoPlayer
            _error.value = "Preview is unavailable. Open the rendered video."
        }
    }

    fun toggle() {
        if (released) return
        val p = _activePlayer.value
        runCatching {
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
                p.play()
            }
        }.onFailure { t ->
            Log.e(TAG, "Preview toggle failed", t)
            _error.value = "Preview is unavailable. Open the rendered video."
        }
    }

    fun seek(ms: Long) {
        if (released) return
        runCatching { _activePlayer.value.seekTo(ms.coerceAtLeast(0L)) }
            .onFailure { Log.w(TAG, "Preview seek failed", it) }
    }

    fun position(): Long = if (released) 0L else runCatching { _activePlayer.value.currentPosition.coerceAtLeast(0L) }.getOrDefault(0L)

    fun pause() {
        if (released) return
        runCatching { exoPlayer.pause() }
        runCatching { compositionPlayer?.pause() }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { exoPlayer.removeListener(listener); exoPlayer.release() }
        runCatching { compositionPlayer?.removeListener(listener); compositionPlayer?.release() }
        compositionPlayer = null
    }

    companion object {
        private const val TAG = "ReelifyPreview"
    }
}

@Composable
fun PlayerSurface(controller: PreviewController, modifier: Modifier = Modifier) {
    val activePlayer by controller.activePlayer.collectAsStateWithLifecycle()
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                player = activePlayer
            }
        },
        update = { view ->
            if (view.player !== activePlayer) view.player = activePlayer
        },
        modifier = modifier,
        onRelease = { it.player = null },
    )
}

@Composable
fun rememberPreviewController(context: Context): PreviewController {
    val controller = remember { PreviewController(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                controller.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }
    return controller
}
