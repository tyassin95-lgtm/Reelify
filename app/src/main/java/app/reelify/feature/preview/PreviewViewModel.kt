package app.reelify.feature.preview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.Composition
import app.reelify.AppContainer
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.EditSettings
import app.reelify.core.model.ProjectBundle
import app.reelify.feature.create.directorContext
import app.reelify.integration.openai.EditGuidanceApplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PreviewUi(
    val composition: Composition? = null,
    val proxyFile: File? = null,
    val exportedFile: File? = null,
    val timeline: ResolvedTimeline? = null,
    val building: Boolean = true,
    val renderProgress: Int? = null,
    val buildError: String? = null,
    val aiBusy: Boolean = false,
    val aiSummary: String? = null,
    val aiError: String? = null,
    val canUndoAi: Boolean = false,
)

/**
 * Rebuilds a deterministic rendered preview proxy MP4 whenever anything affecting rendering
 * changes (debounced so rapid slider movements don't thrash). Standard ExoPlayer plays the proxy
 * reliably; the exact same RenderInputs drive final export.
 */
class PreviewViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _ui = MutableStateFlow(PreviewUi())
    val ui = _ui.asStateFlow()
    private var beforeAi: EditSettings? = null
    private var aiJob: Job? = null
    private var initialBuild = true

    init {
        viewModelScope.launch {
            bundle.filterNotNull()
                .distinctUntilChanged { a, b ->
                    a.project.edit == b.project.edit &&
                        a.clips == b.clips &&
                        a.project.captions == b.project.captions &&
                        a.sections == b.sections &&
                        a.project.brandSnapshot == b.project.brandSnapshot &&
                        a.project.export == b.project.export
                }
                .collectLatest { b ->
                    if (!initialBuild) delay(350)
                    initialBuild = false
                    rebuild(b)
                }
        }
    }

    fun retryPreview() {
        val b = bundle.value ?: return
        viewModelScope.launch { rebuild(b) }
    }

    private suspend fun rebuild(b: ProjectBundle) {
        val existingExport = b.project.export?.path?.let(::File)?.takeIf { it.exists() && it.length() > 1024 }
        val fallbackProxy = c.previewProxy.latestValidProxy(pid)
        _ui.update { it.copy(building = true, renderProgress = 0, buildError = null, exportedFile = existingExport) }
        try {
            val inputs = withContext(Dispatchers.Default) {
                c.preparer.inputs(pid, proxy = true, normalize = true)
            }
            val comp = withContext(Dispatchers.Default) {
                if (inputs.timeline.video.isEmpty()) null
                else runCatching { c.compositionFactory.build(inputs, RenderPurpose.PREVIEW).composition }.getOrNull()
            }
            _ui.update { it.copy(timeline = inputs.timeline, composition = comp) }

            if (inputs.timeline.video.isEmpty()) {
                _ui.update { it.copy(building = false, renderProgress = null, proxyFile = null) }
                return
            }

            val proxy = c.previewProxy.getOrRenderProxy(pid, inputs) { pct ->
                _ui.update { it.copy(renderProgress = pct) }
            }
            _ui.update {
                it.copy(
                    proxyFile = proxy,
                    composition = comp,
                    timeline = inputs.timeline,
                    exportedFile = existingExport,
                    building = false,
                    renderProgress = null,
                    buildError = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Preview rebuild failed for project=$pid", t)
            _ui.update {
                it.copy(
                    building = false,
                    renderProgress = null,
                    proxyFile = fallbackProxy,
                    exportedFile = existingExport,
                    buildError = t.message ?: "Preview rendering failed.",
                )
            }
        }
    }

    fun edit(t: (EditSettings) -> EditSettings) = viewModelScope.launch { c.projects.updateEdit(pid, transform = t) }

    fun aiEdit(request: String) {
        if (request.isBlank()) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _ui.update { it.copy(aiBusy = true, aiError = null, aiSummary = null) }
            try {
                val b = c.projects.getBundle(pid)!!
                val g = c.director.editGuidance(c.directorContext(b.project), request, EditGuidanceApplier.describe(b.project.edit))
                beforeAi = b.project.edit
                c.projects.updateEdit(pid) { EditGuidanceApplier.apply(it, g) }
                _ui.update { it.copy(aiBusy = false, aiSummary = g.summary, canUndoAi = true) }
            } catch (e: CancellationException) {
                _ui.update { it.copy(aiBusy = false) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(aiBusy = false, aiError = e.message) }
            }
        }
    }

    fun undoAi() {
        val prev = beforeAi ?: return
        viewModelScope.launch {
            c.projects.updateEdit(pid) { prev }
            beforeAi = null
            _ui.update { it.copy(canUndoAi = false, aiSummary = null) }
        }
    }

    fun resetToTemplate(group: app.reelify.core.model.EditGroup) = viewModelScope.launch {
        c.projects.resetEditToTemplate(pid, setOf(group))
    }

    fun saveCaptionPreset(name: String) = viewModelScope.launch {
        val style = bundle.value?.project?.edit?.captions?.style ?: return@launch
        c.settings.saveCaptionPreset(
            app.reelify.core.data.SavedCaptionPreset(
                app.reelify.core.util.Ids.new(),
                name.ifBlank { "My style" },
                style.copy(preset = app.reelify.core.model.CaptionPresetId.CUSTOM),
            ),
        )
    }

    fun applyBeautyToTalkingClips(settings: app.reelify.core.model.BeautySettings) = viewModelScope.launch {
        val b = c.projects.getBundle(pid) ?: return@launch
        b.clips.filter { !it.isClinical && it.role == app.reelify.core.model.ClipRole.PRIMARY && it.kind == app.reelify.core.model.MediaKind.VIDEO }
            .forEach { c.projects.upsertClip(it.copy(beauty = settings)) }
    }

    companion object {
        private const val TAG = "ReelifyPreviewVM"
    }
}
