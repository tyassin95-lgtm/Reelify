package app.reelify.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.EditSettings
import app.reelify.core.model.ProjectBundle
import app.reelify.feature.create.directorContext
import app.reelify.integration.openai.EditGuidanceApplier
import androidx.media3.transformer.Composition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

data class PreviewUi(
    val composition: Composition? = null,
    val timeline: ResolvedTimeline? = null,
    val building: Boolean = true,
    val buildError: String? = null,
    val aiBusy: Boolean = false,
    val aiSummary: String? = null,
    val aiError: String? = null,
    val canUndoAi: Boolean = false,
)

/**
 * Rebuilds the preview Composition whenever anything that affects rendering changes. The same
 * builder + inputs are used by export, so what you see is what is rendered.
 */
class PreviewViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _ui = MutableStateFlow(PreviewUi())
    val ui = _ui.asStateFlow()
    private var beforeAi: EditSettings? = null
    private var aiJob: Job? = null

    init {
        viewModelScope.launch {
            bundle.filterNotNull().distinctUntilChanged { a, b -> a.project.edit == b.project.edit && a.clips == b.clips && a.project.captions == b.project.captions && a.sections == b.sections && a.project.brandSnapshot == b.project.brandSnapshot }
                .collectLatest { rebuild() }
        }
    }

    private suspend fun rebuild() {
        _ui.update { it.copy(building = true, buildError = null) }
        runCatching {
            withContext(Dispatchers.Default) {
                val inputs = c.preparer.inputs(pid, proxy = c.settings.current().proxyPreview, normalize = true)
                inputs.timeline to if (inputs.timeline.video.isEmpty()) null else c.compositionFactory.build(inputs, RenderPurpose.PREVIEW).composition
            }
        }.onSuccess { (tl, comp) -> _ui.update { it.copy(composition = comp, timeline = tl, building = false) } }
            .onFailure { e -> if (e is CancellationException) throw e; _ui.update { it.copy(building = false, buildError = e.message) } }
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
            } catch (e: CancellationException) { _ui.update { it.copy(aiBusy = false) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(aiBusy = false, aiError = e.message) } }
        }
    }

    fun undoAi() { val prev = beforeAi ?: return; viewModelScope.launch { c.projects.updateEdit(pid) { prev }; beforeAi = null; _ui.update { it.copy(canUndoAi = false, aiSummary = null) } } }

    fun saveCaptionPreset(name: String) = viewModelScope.launch {
        val style = bundle.value?.project?.edit?.captions?.style ?: return@launch
        c.settings.saveCaptionPreset(app.reelify.core.data.SavedCaptionPreset(app.reelify.core.util.Ids.new(), name.ifBlank { "My style" }, style.copy(preset = app.reelify.core.model.CaptionPresetId.CUSTOM)))
    }

    fun applyBeautyToTalkingClips(settings: app.reelify.core.model.BeautySettings) = viewModelScope.launch {
        val b = c.projects.getBundle(pid) ?: return@launch
        b.clips.filter { !it.isClinical && it.role == app.reelify.core.model.ClipRole.PRIMARY && it.kind == app.reelify.core.model.MediaKind.VIDEO }
            .forEach { c.projects.upsertClip(it.copy(beauty = settings)) }
    }
}
