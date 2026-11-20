package app.reelify.feature.review

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.media.ImportOptions
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.ClipRole
import app.reelify.core.model.FillStrategy
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingImport(val sectionId: String, val role: ClipRole, val uris: List<Uri>, val clinicalHint: Boolean, val replaceClipId: String? = null)

data class FootageUi(val importing: Boolean = false, val error: String? = null, val pending: PendingImport? = null, val message: String? = null)

class FootageViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timeline: StateFlow<ResolvedTimeline?> = c.projects.observeBundle(pid).map { b -> b?.let { c.preparer.resolve(it) } }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _ui = MutableStateFlow(FootageUi())
    val ui = _ui.asStateFlow()

    /** Step 1: user picked media → ask about patient identifiability/consent before accepting. */
    fun picked(section: ReelSection, role: ClipRole, uris: List<Uri>, replaceClipId: String? = null) {
        if (uris.isEmpty()) return
        _ui.update { it.copy(pending = PendingImport(section.id, role, uris, section.shotType.isClinical || role == ClipRole.BROLL, replaceClipId)) }
    }

    fun cancelPending() = _ui.update { it.copy(pending = null) }

    /** Step 2: import with the consent answer. */
    fun confirmImport(clinical: Boolean, identifiable: Boolean, consent: Boolean) {
        val p = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null, importing = true, error = null) }
        viewModelScope.launch {
            val cam = c.settings.current().camera
            var ok = 0
            val errors = mutableListOf<String>()
            for (uri in p.uris) {
                runCatching {
                    val clip = c.importer.import(pid, p.sectionId, uri, ImportOptions(p.role, clinical, identifiable, consent, beauty = cam.beauty.copy(enabled = false)))
                    p.replaceClipId?.let { old ->
                        val prev = c.projects.getClip(old)
                        if (prev != null) { c.projects.upsertClip(clip.copy(order = prev.order)); c.projects.deleteClip(pid, old) }
                    }
                }.onSuccess { ok++ }.onFailure { errors += (it.message ?: "Import failed") }
            }
            _ui.update { it.copy(importing = false, error = errors.distinct().joinToString("\n").ifBlank { null }, message = if (ok > 0) "Imported $ok item${if (ok > 1) "s" else ""}" else null) }
        }
    }

    fun setMediaPlan(section: ReelSection, plan: MediaPlan) = viewModelScope.launch { c.projects.updateSectionQuiet(pid, section.id) { it.copy(mediaPlan = plan) } }

    fun delete(clip: MediaClip) = viewModelScope.launch { c.projects.deleteClip(pid, clip.id) }

    fun toggleMute(clip: MediaClip) = viewModelScope.launch { c.projects.upsertClip(clip.copy(muted = !clip.muted)) }

    fun confirmConsent(clip: MediaClip) = viewModelScope.launch { c.projects.upsertClip(clip.copy(consent = app.reelify.core.model.ConsentState.CONFIRMED)) }

    fun setFill(section: ReelSection, fill: FillStrategy) = viewModelScope.launch { c.projects.updateEdit(pid) { e -> e.copy(fill = e.fill + (section.id to fill)) } }

    /** Missing-media recovery: point an existing clip at a newly picked file, keeping trims/settings. */
    fun relink(clip: MediaClip, uri: Uri) = viewModelScope.launch {
        runCatching {
            val info = c.probe.probe(uri)
            val ref = c.storage.secureImport(pid, uri)
            c.projects.upsertClip(clip.copy(uri = ref, sourceDurationMs = info.durationMs, width = info.width, height = info.height, rotationDegrees = info.rotation, hasAudio = info.hasAudio, missing = false,
                trimEndMs = clip.trimEndMs.coerceAtMost(info.durationMs).let { if (it <= clip.trimStartMs) info.durationMs else it }))
        }.onFailure { e -> _ui.update { it.copy(error = e.message) } }
    }

    fun isAccessible(clip: MediaClip) = c.storage.isAccessible(clip.uri)
    fun dismiss() = _ui.update { it.copy(error = null, message = null) }
}
