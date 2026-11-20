package app.reelify.core.pipeline

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.reelify.core.captions.SrtWriter
import app.reelify.core.data.ProjectRepository
import app.reelify.core.data.SettingsRepository
import app.reelify.core.media.engine.CompositionFactory
import app.reelify.core.media.engine.ExportValidator
import app.reelify.core.media.engine.RenderPreparer
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.TransformerRunner
import app.reelify.core.model.ExportInfo
import app.reelify.core.model.ExportQuality
import app.reelify.core.storage.InsufficientStorageException
import app.reelify.core.storage.MediaStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportPhase { IDLE, PREPARING, RENDERING, VALIDATING, SAVING, DONE, FAILED, CANCELLED }

data class ExportState(
    val projectId: String? = null,
    val phase: ExportPhase = ExportPhase.IDLE,
    val progress: Int = 0,
    val message: String = "",
    val file: File? = null,
    val galleryUri: Uri? = null,
    val checks: List<String> = emptyList(),
    val error: String? = null,
) {
    val running: Boolean get() = phase in setOf(ExportPhase.PREPARING, ExportPhase.RENDERING, ExportPhase.VALIDATING, ExportPhase.SAVING)
}

/**
 * Owns long-running exports. Work runs in an application-scoped coroutine while [ExportService]
 * keeps the process in the foreground, so leaving the screen doesn't kill the render.
 */
class ExportManager(
    private val context: Context,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val storage: MediaStorage,
    private val preparer: RenderPreparer,
    private val factory: CompositionFactory,
    private val runner: TransformerRunner,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(projectId: String, quality: ExportQuality? = null) {
        if (_state.value.running) return
        _state.value = ExportState(projectId, ExportPhase.PREPARING, 0, "Preparing render")
        ContextCompat.startForegroundService(context, Intent(context, ExportService::class.java))
        job = scope.launch { runExport(projectId, quality) }
    }

    fun cancel() {
        job?.cancel()
    }

    fun reset() { if (!_state.value.running) _state.value = ExportState() }

    private suspend fun runExport(projectId: String, quality: ExportQuality?) {
        var out: File? = null
        try {
            val inputs = preparer.inputs(projectId, quality)
            if (!inputs.timeline.isRenderable) throw IllegalStateException(inputs.timeline.blocking.joinToString("\n") { it.message }.ifBlank { "Nothing to render yet." })
            val q = quality ?: inputs.bundle.project.edit.quality
            val estimate = q.bitrate.toLong() / 8 * (inputs.timeline.totalMs / 1000) * 13 / 10 + 40L * 1024 * 1024
            if (storage.freeBytes() < estimate) throw InsufficientStorageException(estimate)

            val built = factory.build(inputs, RenderPurpose.EXPORT)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            out = File(storage.exportDir(projectId), "Reelify_$stamp.mp4")
            _state.value = _state.value.copy(phase = ExportPhase.RENDERING, message = "Rendering ${q.width}×${q.height}")
            runner.export(built.composition, out, q.bitrate, audioOnly = false) { p ->
                _state.value = _state.value.copy(progress = p, message = "Rendering · $p%")
            }

            _state.value = _state.value.copy(phase = ExportPhase.VALIDATING, progress = 100, message = "Validating output")
            val p = inputs.bundle.project
            val captionsExpected = p.edit.captions.enabled && p.edit.captions.burnIn && inputs.captions.isNotEmpty()
            val overlaysExpected = p.edit.branding.enabled && inputs.brand?.isConfigured == true
            val report = withContext(Dispatchers.IO) {
                ExportValidator.validate(
                    out, q.width, q.height, inputs.timeline.totalMs, q.fps,
                    expectAudio = inputs.timeline.dialogue.isNotEmpty() || inputs.musicUri != null,
                    captionsExpected = captionsExpected, captionFramesDrawn = built.overlayRenderer?.captionRenders ?: 0,
                    overlaysExpected = overlaysExpected, overlayFramesDrawn = built.overlayRenderer?.overlayRenders ?: 0,
                )
            }
            if (!report.ok) {
                _state.value = _state.value.copy(phase = ExportPhase.FAILED, checks = report.summary, file = out, error = "The rendered file didn't pass validation.")
                return
            }

            var srt: File? = null
            if (p.edit.captions.exportSrt && inputs.captions.isNotEmpty()) {
                srt = File(out.parentFile, out.nameWithoutExtension + ".srt").apply { writeText(SrtWriter.write(inputs.captions)) }
            }
            _state.value = _state.value.copy(phase = ExportPhase.SAVING, message = "Saving to gallery")
            val gallery = if (settings.current().saveToGallery) saveToGallery(out, srt) else null
            projects.updateExport(projectId, ExportInfo(out.absolutePath, gallery?.toString(), srt?.absolutePath, System.currentTimeMillis(), report.durationMs, report.width, report.height, report.sizeBytes, p.edit.editVersion, report.summary))
            _state.value = _state.value.copy(phase = ExportPhase.DONE, file = out, galleryUri = gallery, checks = report.summary, message = "Export complete")
        } catch (e: CancellationException) {
            out?.delete()
            _state.value = _state.value.copy(phase = ExportPhase.CANCELLED, message = "Export cancelled", error = null)
        } catch (e: Exception) {
            out?.takeIf { it.exists() && it.length() < 1024 }?.delete()
            _state.value = _state.value.copy(phase = ExportPhase.FAILED, error = e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun saveToGallery(file: File, srt: File?): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Reelify")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { os -> file.inputStream().use { it.copyTo(os) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            srt?.let { s ->
                val sv = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, s.name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Reelify")
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, sv)?.let { su -> resolver.openOutputStream(su)?.use { os -> s.inputStream().use { it.copyTo(os) } } }
            }
            uri
        }.getOrNull()
    }
}
