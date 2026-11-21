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
 * Effect-free single-clip core trimmer player backed by plain Media3 [ExoPlayer].
 *
 * By design, standard trimming NEVER attaches Media3 GL video effects (`Presentation`, beauty
 * shaders, or color LUTs) to `ExoPlayer`, eliminating OpenGL surface/effect initialization crashes
 * on all devices. Framing preview is handled cleanly via Compose `graphicsLayer`, and optional
 * GPU filter preview can be enabled separately behind a guarded opt-in toggle.
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

    private val _discoveredDurationMs = MutableStateFlow(0L)
    val discoveredDurationMs: StateFlow<Long> = _discoveredDurationMs.asStateFlow()

    /** True when the active session has attached zero Media3 GL video effects (default mode). */
    var isEffectFree: Boolean = true
        private set

    var durationMs: Long = 0L
        private set

    private var lastUri: Uri? = null
    private var lastStartMs: Long = 0L
    private var lastVolume: Float = 1f
    private var effectsSupported: Boolean = true
    private var released: Boolean = false

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playing.value = isPlaying
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Trim ExoPlayer error code=${error.errorCodeName}", error)
            if (!isEffectFree && effectsSupported) {
                // Immediately revert to effect-free plain ExoPlayer if optional GL preview failed.
                effectsSupported = false
                isEffectFree = true
                _notice.value = "Live filter preview isn't supported for this clip; using clean trim playback (effects apply at export)."
                lastUri?.let { uri ->
                    loadEffectFreeInternal(uri, position().coerceAtLeast(lastStartMs), lastVolume, play = false)
                }
                return
            }
            _error.value = "Couldn't play this clip (${error.errorCodeName}). You can replace it or return safely."
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                _ready.value = true
                val probed = runCatching { player.duration }.getOrDefault(0L)
                    .takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET } ?: 0L
                if (probed > 0L) {
                    durationMs = probed
                    _discoveredDurationMs.value = probed
                }
            }
        }
    }

    init {
        player.addListener(listener)
    }

    /** Loads media into plain effect-free ExoPlayer (no GL effects pipeline initialized). */
    fun load(
        uri: Uri,
        startPositionMs: Long = 0L,
        volume: Float = 1f,
        play: Boolean = false,
    ) {
        if (released) return
        lastUri = uri
        lastStartMs = startPositionMs.coerceAtLeast(0L)
        lastVolume = volume.coerceIn(0f, 1f)
        loadEffectFreeInternal(uri, lastStartMs, lastVolume, play)
    }

    /**
     * Optional secondary GL preview path (never called during default trimming). Falls back
     * immediately to effect-free playback if effect creation or player attachment throws.
     */
    fun applyOptionalPreviewEffects(
        uri: Uri,
        startPositionMs: Long,
        volume: Float,
        effectsProvider: () -> List<Effect>,
    ) {
        if (released) return
        lastUri = uri
        lastStartMs = startPositionMs.coerceAtLeast(0L)
        lastVolume = volume.coerceIn(0f, 1f)
        if (!effectsSupported) {
            loadEffectFreeInternal(uri, lastStartMs, lastVolume, play = false)
            return
        }
        val builtEffects = runCatching(effectsProvider).getOrElse { t ->
            Log.w(TAG, "Optional trim effect creation failed; staying effect-free", t)
            effectsSupported = false
            _notice.value = "Live filter preview isn't available on this device; beauty still applies at export."
            loadEffectFreeInternal(uri, lastStartMs, lastVolume, play = false)
            return
        }
        if (builtEffects.isEmpty()) {
            loadEffectFreeInternal(uri, lastStartMs, lastVolume, play = false)
            return
        }
        _error.value = null
        _ready.value = false
        runCatching {
            player.setVideoEffects(builtEffects)
            isEffectFree = false
            player.volume = lastVolume
            player.setMediaItem(MediaItem.fromUri(uri), lastStartMs)
            player.prepare()
            player.playWhenReady = false
        }.onFailure { t ->
            Log.w(TAG, "Optional trim ExoPlayer effects failed; reverting to effect-free playback", t)
            effectsSupported = false
            isEffectFree = true
            _notice.value = "Live filter preview isn't supported for this clip; using clean trim playback."
            loadEffectFreeInternal(uri, lastStartMs, lastVolume, play = false)
        }
    }

    private fun loadEffectFreeInternal(
        uri: Uri,
        startPositionMs: Long,
        volume: Float,
        play: Boolean,
    ) {
        _error.value = null
        _ready.value = false
        isEffectFree = true
        runCatching {
            // Note: do NOT call setVideoEffects() in default mode so Media3 never spins up VideoFrameProcessor.
            player.volume = volume.coerceIn(0f, 1f)
            player.setMediaItem(MediaItem.fromUri(uri), startPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = play
        }.onFailure { t ->
            Log.e(TAG, "Failed to load clip in effect-free TrimPlayerController", t)
            _error.value = "Couldn't open this clip for trimming (${t.message ?: t.javaClass.simpleName})."
        }
    }

    fun previewSelection(startMs: Long) {
        if (released) return
        runCatching {
            val safeStart = startMs.coerceAtLeast(0L)
            lastStartMs = safeStart
            player.seekTo(safeStart)
            player.play()
        }.onFailure { Log.e(TAG, "Preview selection failed", it) }
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
        runCatching {
            player.removeListener(listener)
            player.release()
        }.onFailure { Log.w(TAG, "Error releasing trim ExoPlayer", it) }
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
