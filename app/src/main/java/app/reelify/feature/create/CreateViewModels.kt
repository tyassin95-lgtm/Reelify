package app.reelify.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.model.CreativeFeedback
import app.reelify.core.model.Project
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.EditSettings
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.CaptionStyle
import app.reelify.integration.openai.DirectorContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SetupViewModel(private val c: AppContainer) : ViewModel() {
    val treatments = c.library.observeTreatments()

    suspend fun create(language: ReelLanguage, treatmentId: String, duration: ReelDuration, style: ReelStyle): Project {
        val t = c.library.getTreatment(treatmentId)
        val s = c.settings.current()
        val edit = EditSettings(captions = CaptionSettings(style = CaptionStyle.preset(s.defaultCaptionPreset)), quality = s.exportQuality)
        return c.projects.create(language, treatmentId, duration, style, "${t?.name ?: "Reel"} · ${style.label}", edit)
    }
}

suspend fun AppContainer.directorContext(p: Project): DirectorContext = DirectorContext(
    language = p.language, duration = p.duration, style = p.style,
    treatment = library.getTreatment(p.treatmentId), terminology = library.terminology(),
    brand = settings.currentBrand(), concept = p.concept, feedback = p.feedback,
)

data class IdeaUi(val project: Project? = null, val concept: ReelConcept = ReelConcept(), val loading: Boolean = false, val error: String? = null)

class IdeaViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    private val _ui = MutableStateFlow(IdeaUi())
    val ui = _ui.asStateFlow()
    private var saveJob: Job? = null
    private var analyzeJob: Job? = null

    init {
        viewModelScope.launch {
            val p = c.projects.getProject(pid)
            _ui.update { it.copy(project = p, concept = p?.concept ?: ReelConcept()) }
        }
    }

    fun edit(t: (ReelConcept) -> ReelConcept) {
        _ui.update { it.copy(concept = t(it.concept)) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); c.projects.updateConcept(pid, _ui.value.concept) } // autosave
    }

    fun analyze(onDone: () -> Unit) {
        if (_ui.value.concept.summary.isBlank()) { _ui.update { it.copy(error = "Describe your idea first.") }; return }
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                c.projects.updateConcept(pid, _ui.value.concept)
                val p = c.projects.getProject(pid)!!
                val fb = c.director.analyzeIdea(c.directorContext(p))
                c.projects.updateFeedback(pid, fb)
                _ui.update { it.copy(loading = false) }
                onDone()
            } catch (e: CancellationException) {
                _ui.update { it.copy(loading = false) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = e.message ?: "Analysis failed") }
            }
        }
    }

    fun cancel() { analyzeJob?.cancel(); _ui.update { it.copy(loading = false) } }
    fun dismissError() = _ui.update { it.copy(error = null) }
}

data class FeedbackUi(
    val project: Project? = null,
    val feedback: CreativeFeedback? = null,
    val acceptedImprovements: Set<Int> = emptySet(),
    val busy: String? = null,
    val error: String? = null,
)

class FeedbackViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    private val _ui = MutableStateFlow(FeedbackUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val p = c.projects.getProject(pid)
            _ui.update { it.copy(project = p, feedback = p?.feedback, acceptedImprovements = p?.feedback?.improvements?.indices?.toSet() ?: emptySet()) }
        }
    }

    fun edit(t: (CreativeFeedback) -> CreativeFeedback) {
        val f = _ui.value.feedback ?: return
        _ui.update { it.copy(feedback = t(f)) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); c.projects.updateFeedback(pid, _ui.value.feedback) }
    }

    fun toggleImprovement(i: Int) = _ui.update { s -> s.copy(acceptedImprovements = if (i in s.acceptedImprovements) s.acceptedImprovements - i else s.acceptedImprovements + i) }

    fun regenerate(instruction: String?) = launchBusy("Rethinking the creative direction…") {
        val p = c.projects.getProject(pid)!!
        val fb = c.director.analyzeIdea(c.directorContext(p), instruction?.takeIf { it.isNotBlank() })
        c.projects.updateFeedback(pid, fb)
        _ui.update { it.copy(feedback = fb, acceptedImprovements = fb.improvements.indices.toSet()) }
    }

    fun generateScript(onDone: () -> Unit) = launchBusy("Writing your three-section script…") {
        val f = _ui.value.feedback ?: error("No creative direction yet")
        val approved = f.copy(improvements = f.improvements.filterIndexed { i, _ -> i in _ui.value.acceptedImprovements }, approved = true)
        c.projects.updateFeedback(pid, approved)
        val p = c.projects.getProject(pid)!!
        val bundle = c.projects.getBundle(pid)!!
        val plan = c.director.generatePlan(c.directorContext(p), bundle.sections)
        c.projects.applyPlan(pid, plan.meta, plan.sections, "initial generation")
        onDone()
    }

    private fun launchBusy(label: String, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(busy = label, error = null) }
            try { block(); _ui.update { it.copy(busy = null) } }
            catch (e: CancellationException) { _ui.update { it.copy(busy = null) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(busy = null, error = e.message ?: "Something went wrong") } }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismissError() = _ui.update { it.copy(error = null) }
}
