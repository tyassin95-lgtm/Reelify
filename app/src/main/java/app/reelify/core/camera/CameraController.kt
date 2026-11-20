package app.reelify.core.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.reelify.core.media.engine.FaceUniform
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class CameraCaps(
    val hasFront: Boolean = false,
    val hasBack: Boolean = false,
    val hasFlash: Boolean = false,
    val exposureRange: IntRange = 0..0,
    val exposureStep: Float = 0f,
    val supports4k: Boolean = false,
    val supports1080: Boolean = false,
    val supports60fps: Boolean = false,
    val awbModes: List<Int> = emptyList(),
    val maxZoom: Float = 1f,
)

/** Face framing feedback in normalized preview coordinates (top-left origin). */
data class FaceInfo(val cx: Float, val cy: Float, val w: Float, val h: Float)

sealed interface RecState {
    data object Idle : RecState
    data class Recording(val elapsedMs: Long, val amplitude: Double) : RecState
    data class Finished(val file: File, val durationMs: Long, val warning: String?) : RecState
    data class Failed(val message: String) : RecState
}

/**
 * CameraX controller: binds Preview (+ beauty effect), VideoCapture and ImageAnalysis, exposes only
 * controls the current camera actually supports, and degrades gracefully (drops analysis or 60 fps)
 * when a device can't bind every use case.
 */
class CameraController(private val context: Context) {
    val beautyProcessor = BeautySurfaceProcessor()
    private val effect = BeautyCameraEffect(beautyProcessor) { _errors.value = "Camera effect error: ${it.message}" }
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL).build(),
    )

    private val _caps = MutableStateFlow(CameraCaps())
    val caps: StateFlow<CameraCaps> = _caps.asStateFlow()
    private val _rec = MutableStateFlow<RecState>(RecState.Idle)
    val rec: StateFlow<RecState> = _rec.asStateFlow()
    private val _face = MutableStateFlow<FaceInfo?>(null)
    val face: StateFlow<FaceInfo?> = _face.asStateFlow()
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    var usingFront = true; private set
    var faceGuideEnabled = true

    @OptIn(ExperimentalCamera2Interop::class)
    suspend fun bind(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, front: Boolean, want4k: Boolean, want60: Boolean) {
        val p = provider ?: ProcessCameraProvider.getInstance(context).await().also { provider = it }
        val hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        val hasBack = runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        if (!hasFront && !hasBack) { _errors.value = "No camera available on this device."; return }
        usingFront = if (front) hasFront else !hasBack
        val selector = if (usingFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val cameraInfo = p.getCameraInfo(selector)

        val caps = Recorder.getVideoCapabilities(cameraInfo)
        val qualities = caps.getSupportedQualities(DynamicRange.SDR)
        val fpsRanges = cameraInfo.supportedFrameRateRanges
        val supports60 = fpsRanges.any { it.upper >= 60 && it.lower >= 30 }
        val c2 = Camera2CameraInfo.from(cameraInfo)
        val awb = c2.getCameraCharacteristic(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()

        val ratio = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
        val preview = Preview.Builder().setResolutionSelector(ratio).build().also { it.surfaceProvider = surfaceProvider }
        val target = if (want4k && Quality.UHD in qualities) Quality.UHD else Quality.FHD
        val recorder = Recorder.Builder()
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .setQualitySelector(QualitySelector.from(target, FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)))
            .build()
        val vcBuilder = VideoCapture.Builder(recorder)
        if (want60 && supports60) vcBuilder.setTargetFrameRate(Range(60, 60))
        val vc = vcBuilder.build()
        val analysis = ImageAnalysis.Builder().setResolutionSelector(ratio).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

        p.unbindAll()
        val cam = runCatching {
            p.bindToLifecycle(owner, selector, UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc).addUseCase(analysis).addEffect(effect).build())
        }.recoverCatching {
            // Some hardware can't run 3 streams: drop face analysis (recording still works).
            p.unbindAll()
            p.bindToLifecycle(owner, selector, UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc).addEffect(effect).build())
        }.recoverCatching {
            p.unbindAll()
            p.bindToLifecycle(owner, selector, UseCaseGroup.Builder().addUseCase(preview).addUseCase(vc).build())
        }.getOrElse { _errors.value = "Couldn't start the camera: ${it.message}"; return }
        camera = cam
        videoCapture = vc
        val exp = cam.cameraInfo.exposureState
        _caps.value = CameraCaps(
            hasFront, hasBack, cam.cameraInfo.hasFlashUnit(),
            if (exp.isExposureCompensationSupported) exp.exposureCompensationRange.lower..exp.exposureCompensationRange.upper else 0..0,
            exp.exposureCompensationStep.toFloat(),
            Quality.UHD in qualities, Quality.FHD in qualities, supports60, awb,
            cam.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f,
        )
        _torch.value = false
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(proxy: androidx.camera.core.ImageProxy) {
        val media = proxy.image
        if (media == null || (!faceGuideEnabled && beautyProcessor.params.isIdentity)) { proxy.close(); return }
        val rot = proxy.imageInfo.rotationDegrees
        val w = if (rot % 180 == 0) proxy.width else proxy.height
        val h = if (rot % 180 == 0) proxy.height else proxy.width
        detector.process(InputImage.fromMediaImage(media, rot))
            .addOnSuccessListener { faces ->
                val f = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (f == null) { _face.value = null; beautyProcessor.face = null; return@addOnSuccessListener }
                val b = f.boundingBox
                var cx = b.exactCenterX() / w; val cy = b.exactCenterY() / h
                if (usingFront) cx = 1f - cx
                _face.value = FaceInfo(cx, cy, b.width().toFloat() / w, b.height().toFloat() / h)
                fun eye(type: Int): Pair<Float, Float> = f.getLandmark(type)?.position?.let { pt ->
                    val x = pt.x / w; (if (usingFront) 1f - x else x) to (1f - pt.y / h)
                } ?: (-1f to -1f)
                val (lx, ly) = eye(FaceLandmark.LEFT_EYE); val (rx, ry) = eye(FaceLandmark.RIGHT_EYE)
                val aspect = w.toFloat() / h
                beautyProcessor.face = FaceUniform(cx, 1f - cy, (b.width().toFloat() / w / 2f) * aspect, b.height().toFloat() / h / 2f, lx, ly, rx, ry)
            }
            .addOnCompleteListener { proxy.close() }
    }

    fun focusAt(point: MeteringPoint) {
        val cam = camera ?: return
        runCatching { cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(4, TimeUnit.SECONDS).build()) }
    }

    fun setExposure(index: Int) { camera?.cameraControl?.setExposureCompensationIndex(index.coerceIn(_caps.value.exposureRange)) }

    fun setTorch(on: Boolean) {
        if (!_caps.value.hasFlash) return
        camera?.cameraControl?.enableTorch(on); _torch.value = on
    }

    fun setZoom(ratio: Float) { camera?.cameraControl?.setZoomRatio(ratio.coerceIn(1f, _caps.value.maxZoom)) }

    @OptIn(ExperimentalCamera2Interop::class)
    fun setWhiteBalance(mode: Int) {
        val cam = camera ?: return
        if (mode !in _caps.value.awbModes) return
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder().setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, mode).build(),
        )
    }

    @SuppressLint("MissingPermission")
    fun startRecording(file: File, withAudio: Boolean) {
        val vc = videoCapture ?: run { _rec.value = RecState.Failed("Camera not ready"); return }
        if (recording != null) return
        var started = 0L
        val pending = vc.output.prepareRecording(context, FileOutputOptions.Builder(file).build()).let { if (withAudio) it.withAudioEnabled() else it }
        recording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> { started = System.currentTimeMillis(); _rec.value = RecState.Recording(0, 0.0) }
                is VideoRecordEvent.Status -> _rec.value = RecState.Recording(event.recordingStats.recordedDurationNanos / 1_000_000, runCatching { event.recordingStats.audioStats.audioAmplitude }.getOrDefault(0.0))
                is VideoRecordEvent.Finalize -> {
                    recording = null
                    val dur = event.recordingStats.recordedDurationNanos / 1_000_000
                    if (!event.hasError() && file.exists()) _rec.value = RecState.Finished(file, dur, null)
                    else if (file.exists() && file.length() > 0 && event.error in setOf(VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE, VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED, VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED, VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE)) {
                        _rec.value = RecState.Finished(file, dur, "Recording stopped early (camera interrupted or storage limit). The take was kept.")
                    } else {
                        file.delete()
                        _rec.value = RecState.Failed(when (event.error) {
                            VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Not enough storage to record."
                            VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "Recording was too short to save."
                            else -> "Recording failed (code ${event.error})."
                        })
                    }
                }
                else -> {}
            }
        }
    }

    fun stopRecording() { recording?.stop() }
    fun resetRecState() { _rec.value = RecState.Idle }
    fun clearError() { _errors.value = null }

    fun unbind() {
        recording?.stop()
        provider?.unbindAll()
    }

    fun release() {
        unbind()
        beautyProcessor.release()
        analysisExecutor.shutdown()
        detector.close()
    }
}
