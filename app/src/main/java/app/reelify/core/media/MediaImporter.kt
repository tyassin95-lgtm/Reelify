package app.reelify.core.media

import android.net.Uri
import app.reelify.core.audio.AudioAnalyzer
import app.reelify.core.audio.SpeechAnalysis
import app.reelify.core.data.ProjectRepository
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ClipOrigin
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ConsentState
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ProjectStatus
import app.reelify.core.storage.MediaStorage
import app.reelify.core.util.Ids
import java.io.File

data class ImportOptions(
    val role: ClipRole,
    val clinical: Boolean = false,
    val identifiablePatient: Boolean = false,
    val consentConfirmed: Boolean = false,
    val beauty: BeautySettings = BeautySettings(),
    val muteOriginal: Boolean = false,
)

/** Turns picked/recorded media into persisted, validated [MediaClip]s with sensible default trims. */
class MediaImporter(
    private val projects: ProjectRepository,
    private val storage: MediaStorage,
    private val probe: MediaProbe,
    private val thumbnails: Thumbnails,
    private val analyzer: AudioAnalyzer,
) {
    suspend fun import(projectId: String, sectionId: String, uri: Uri, opts: ImportOptions, origin: ClipOrigin = ClipOrigin.IMPORT, mimeHint: String? = null): MediaClip {
        val info = probe.probe(uri, mimeHint) // throws UnsupportedMediaException with a readable message
        val ref = if (origin == ClipOrigin.CAMERA) (uri.path ?: uri.toString()) else storage.secureImport(projectId, uri)
        val bundle = projects.getBundle(projectId) ?: error("Project not found")
        val sectionMs = bundle.project.duration.sectionMs
        val id = Ids.new()

        var trimStart = 0L
        var trimEnd = info.durationMs
        if (info.kind == MediaKind.VIDEO && opts.role == ClipRole.PRIMARY && info.durationMs > sectionMs) {
            // Smart default trim using speech/silence cues; the user can adjust in the trim screen.
            val env = if (info.hasAudio && !opts.muteOriginal) analyzer.envelope(storage.toUri(ref)) else null
            if (env != null) {
                val s = SpeechAnalysis.suggestTrim(env, info.durationMs, sectionMs)
                trimStart = s.startMs; trimEnd = s.endMs
            } else trimEnd = sectionMs
        } else if (info.kind == MediaKind.VIDEO && opts.role == ClipRole.BROLL) {
            trimEnd = minOf(info.durationMs, maxOf(2500L, sectionMs / 2))
        } else if (info.kind == MediaKind.IMAGE) {
            trimEnd = 0 // images stretch to fill (or use explicit duration)
        }

        val thumb = runCatching {
            val bmp = if (info.kind == MediaKind.IMAGE) thumbnails.image(storage.toUri(ref), 360, info.rotation)
            else if (info.kind == MediaKind.VIDEO) thumbnails.videoFrame(storage.toUri(ref), (trimStart + 300).coerceAtMost(info.durationMs))
            else null
            bmp?.let { thumbnails.saveJpeg(it, File(storage.thumbDir(projectId), "$id.jpg")).absolutePath }
        }.getOrNull()

        val clip = MediaClip(
            id = id, projectId = projectId, sectionId = sectionId, role = opts.role,
            order = projects.nextOrder(projectId, sectionId, opts.role),
            kind = info.kind, origin = origin, uri = ref, displayName = storage.displayName(uri),
            sourceDurationMs = info.durationMs, width = info.width, height = info.height, rotationDegrees = info.rotation,
            frameRate = info.frameRate, hasAudio = info.hasAudio, videoMime = info.videoMime,
            trimStartMs = trimStart, trimEndMs = trimEnd,
            muted = opts.muteOriginal || opts.role == ClipRole.BROLL,
            // Beauty is never applied by default to clinical / before-after footage.
            beauty = if (opts.clinical) BeautySettings(enabled = false) else opts.beauty,
            isClinical = opts.clinical, identifiablePatient = opts.identifiablePatient,
            consent = when {
                !opts.identifiablePatient -> ConsentState.NOT_REQUIRED
                opts.consentConfirmed -> ConsentState.CONFIRMED
                else -> ConsentState.NOT_CONFIRMED
            },
            thumbnailPath = thumb, createdAt = System.currentTimeMillis(),
        )
        projects.upsertClip(clip)
        projects.update(projectId) { if (it.status == ProjectStatus.SCRIPTED || it.status == ProjectStatus.DRAFT) it.copy(status = ProjectStatus.FILMING) else it }
        return clip
    }
}
