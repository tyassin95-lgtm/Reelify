package app.reelify.feature.flow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TopicSuggestion
import app.reelify.core.data.LastReelChoice
import app.reelify.core.media.ImportOptions
import app.reelify.core.model.CaptionSettings
import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.ClipRole
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ProjectStatus
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.Treatment
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.SectionScript
import app.reelify.core.template.ShotRole
import app.reelify.integration.openai.FreshIdea
import app.reelify.integration.openai.ScriptAction
import app.reelify.integration.openai.ScriptContext
import app.reelify.integration.openai.ScriptValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Builds the scriptwriter context for a template project. */
suspend fun AppContainer.scriptContext(p: Project, topicTitle: String, brief: String = "", idea: String = ""): ScriptContext {
    val template = p.template ?: error("Not a template project")
    return ScriptContext(
        language = p.language, duration = p.duration, template = template,
        treatment = library.getTreatment(p.treatmentId), terminology = library.terminology(), brand = settings.currentBrand(),
        topicTitle = topicTitle, topicBrief = brief, userIdea = idea, concept = p.concept,
        recentHooks = content.recentHooks(), recentTopics = content.recentTopicKeys(p.treatmentId),
        examples = if (p.language == ReelLanguage.IRAQI_ARABIC) content.iraqiExamplesForPrompt() else emptyList(),
    )
}

// =========================================================================================== Stage 1

class ChooseReelViewModel(private val c: AppContainer) : ViewModel() {
    val treatments = c.library.observeTreatments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val templates = c.templates.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun last(): LastReelChoice = c.settings.current().lastChoice

    suspend fun create(language: ReelLanguage, treatment: Treatment, duration: ReelDuration, template: ReelTemplate, arabicUi: Boolean): Project {
        c.settings.update { it.copy(lastChoice = LastReelChoice(treatment.id, language, duration.totalMs, template.id)) }
        val s = c.settings.current()
        val base = EditSettings(captions = CaptionSettings(), quality = s.exportQuality)
        val name = if (language == ReelLanguage.IRAQI_ARABIC) treatment.arabicName.ifBlank { treatment.name } else treatment.name
        return c.projects.createFromTemplate(language, treatment.id, duration, template, "$name · ${template.name.resolve(arabicUi)}", base)
    }
}

// =========================================================================================== Stage 2

data class MessageUi(
    val project: Project? = null,
    val treatment: Treatment? = null,
    val suggestions: List<TopicSuggestion> = emptyList(),
    val ideas: List<FreshIdea> = emptyList(),
    val ideasLoading: Boolean = false,
    val generating: String? = null,
    val generatingKey: String? = null,
    val error: String? = null,
    val concept: ReelConcept = ReelConcept(),
    val profileReady: Boolean = false,
)

class MessageViewModel(private val c: AppContainer, private val pid: String, private val arabicUi: Boolean) : ViewModel() {
    private val _ui = MutableStateFlow(MessageUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            val p = c.projects.getProject(pid) ?: return@launch
            val t = c.library.getTreatment(p.treatmentId)
            val recent = c.content.recentTopicKeys(p.treatmentId)
            val arabicTitles = p.language == ReelLanguage.IRAQI_ARABIC
            _ui.update {
                it.copy(project = p, treatment = t, concept = p.concept, profileReady = TopicLibrary.profileReady(t),
                    suggestions = TopicLibrary.suggestions(t, p.template?.id, recent, arabicTitles))
            }
        }
    }

    fun editConcept(t: (ReelConcept) -> ReelConcept) {
        _ui.update { it.copy(concept = t(it.concept)) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(400); c.projects.updateConcept(pid, _ui.value.concept) }
    }

    fun surpriseMe() {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(ideasLoading = true, error = null) }
            try {
                val p = c.projects.getProject(pid)!!
                val ctx = c.scriptContext(p, topicTitle = "(propose fresh ideas)")
                _ui.update { it.copy(ideas = c.scriptWriter.ideas(ctx), ideasLoading = false) }
            } catch (e: CancellationException) { _ui.update { it.copy(ideasLoading = false) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(ideasLoading = false, error = e.message ?: "Couldn't get ideas") } }
        }
    }

    fun useTopic(s: TopicSuggestion, onDone: () -> Unit) = generate(s.title, s.idea.brief, "", s.idea.key, null, onDone)
    fun useIdea(i: FreshIdea, onDone: () -> Unit) = generate(i.title, i.angle, "", "ai:" + i.title.take(40), i.hook, onDone)
    fun useOwnIdea(text: String, onDone: () -> Unit) { if (text.isNotBlank()) generate(text.take(120), "", text, "own", null, onDone) }

    private fun generate(title: String, brief: String, idea: String, key: String, hook: String?, onDone: () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(generating = title, generatingKey = key, error = null) }
            try {
                c.projects.updateConcept(pid, _ui.value.concept.copy(summary = idea.ifBlank { title }))
                val p = c.projects.getProject(pid)!!
                val ctx = c.scriptContext(p, title, brief, idea)
                val written = c.scriptWriter.generate(ctx, key, hook)
                val withNotes = if (c.settings.current().aiEditorialReview) {
                    runCatching {
                        val tmp = ProductionPlanner.mergeScript(p.template!!, c.projects.getBundle(pid)!!.sections, written.scripts, false, null, 0)
                        val review = c.scriptWriter.editorialReview(ctx, tmp)
                        written.meta.copy(qualityNotes = written.meta.qualityNotes + review.issues.map { "Shot ${it.sectionIndex}: ${it.problem} → ${it.suggestion}" } +
                            "Editorial review (AI, advisory): naturalness ${review.naturalness}/5, engagement ${review.engagement}/5")
                    }.getOrElse { if (it is CancellationException) throw it; written.meta }
                } else written.meta
                c.projects.applyScript(pid, withNotes, written.scripts, "AI script", respectLocks = true, title = null)
                c.content.recordTopicUse(p.treatmentId, p.template!!.id, key, written.meta.hookOptions.firstOrNull()?.text.orEmpty())
                _ui.update { it.copy(generating = null, generatingKey = null) }
                onDone()
            } catch (e: CancellationException) { _ui.update { it.copy(generating = null, generatingKey = null) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(generating = null, generatingKey = null, error = e.message ?: "Script generation failed") } }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismissError() = _ui.update { it.copy(error = null) }
}

// =========================================================================================== Script review

data class ScriptReviewUi(
    val busy: String? = null,
    val busySections: Set<Int> = emptySet(),
    val error: String? = null,
    val summary: String? = null,
    val notes: List<String> = emptyList(),
)

class ScriptReviewViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timing = c.timingState
    private val _ui = MutableStateFlow(ScriptReviewUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    private fun run(label: String, sections: Set<Int>, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(busy = label, busySections = sections, error = null, summary = null) }
            try { block(); _ui.update { it.copy(busy = null, busySections = emptySet()) } }
            catch (e: CancellationException) { _ui.update { it.copy(busy = null, busySections = emptySet()) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(busy = null, busySections = emptySet(), error = e.message ?: "Something went wrong") } }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismissError() = _ui.update { it.copy(error = null) }

    private suspend fun ctx(b: ProjectBundle) = c.scriptContext(b.project, b.project.script?.topicTitle ?: b.project.concept.summary, idea = b.project.concept.summary)

    private fun selectedHook(b: ProjectBundle): String? = b.project.script?.activeHook?.takeIf { it.isNotBlank() }

    /** Chooses one of the hooks: swaps the opening of shot 1 and nothing else. */
    fun selectHook(index: Int) = viewModelScope.launch {
        val b = c.projects.getBundle(pid) ?: return@launch
        val meta = b.project.script ?: return@launch
        val newHook = meta.hookOptions.getOrNull(index)?.text ?: return@launch
        val s1 = b.sections.first { it.index == 1 }
        if (s1.locked) { _ui.update { it.copy(error = "Shot 1 is locked. Unlock it to change the hook.") }; return@launch }
        val spoken = ScriptValidator.swapHook(s1.spokenScript, selectedHook(b), newHook)
        c.projects.applyScript(pid, meta.copy(selectedHook = index, activeHook = newHook), listOf(SectionScript(1, spoken, s1.onScreenText, s1.captionEmphasis)), "hook changed", onlyIndex = 1)
    }

    /** Fetches three new hooks. Nothing in the script changes until the creator picks one. */
    fun tryDifferentHooks() = run("Writing new hooks…", setOf(1)) {
        val b = c.projects.getBundle(pid)!!
        val meta = b.project.script ?: error("No script yet")
        val hooks = c.scriptWriter.hooks(ctx(b), b.sections, meta.hookOptions, selectedHook(b))
        c.projects.updateScriptMeta(pid) { m -> m.copy(hookOptions = hooks, selectedHook = -1) }
        _ui.update { it.copy(summary = "Three new hooks — tap one to use it.") }
    }

    fun action(action: ScriptAction, targets: Set<Int>, instruction: String? = null) = run(action.label + "…", targets) {
        val b = c.projects.getBundle(pid)!!
        val r = c.scriptWriter.rewrite(ctx(b), b.sections, selectedHook(b), action, targets, instruction)
        c.projects.applyScript(pid, b.project.script!!.copy(qualityNotes = r.notes), r.scripts, "AI: ${action.label}")
        _ui.update { it.copy(summary = r.summary.ifBlank { null }, notes = r.notes) }
    }

    fun saveManual(section: ReelSection, spoken: String, headline: String) = viewModelScope.launch {
        val b = c.projects.getBundle(pid) ?: return@launch
        val clean = app.reelify.core.content.GeneratedContentNormalizer.clean(spoken)
        c.projects.applyScript(pid, b.project.script ?: app.reelify.core.model.ScriptMeta(), listOf(SectionScript(section.index, clean, headline.trim(), section.captionEmphasis)), "manual edit", respectLocks = false, onlyIndex = section.index)
    }

    fun toggleLock(section: ReelSection) = viewModelScope.launch { c.projects.updateSectionQuiet(pid, section.id) { it.copy(locked = !it.locked) } }
    fun undo(section: ReelSection) = viewModelScope.launch { c.projects.undoSection(pid, section.id) }
    suspend fun revisionCount(sectionId: String) = c.projects.revisionCount(sectionId)

    fun localFlags(b: ProjectBundle): List<ClaimFlag> {
        val forbidden = b.project.concept.excludedClaims
        val tpl = b.project.template
        return b.sections.flatMap { s ->
            val myth = tpl?.shots?.firstOrNull { it.index == s.index }?.role == ShotRole.MYTH
            ClaimChecker.check(s.spokenScript + "\n" + s.onScreenText, s.id, forbidden).map { f -> if (myth) f.copy(severity = app.reelify.core.model.ClaimSeverity.WARNING, issue = f.issue + " (inside a myth — make sure it is clearly corrected)") else f }
        }
    }

    fun approve(onDone: () -> Unit) = viewModelScope.launch {
        c.projects.update(pid) { it.copy(planApproved = true, status = if (it.status == ProjectStatus.DRAFT || it.status == ProjectStatus.SCRIPTED) ProjectStatus.FILMING else it.status) }
        onDone()
    }
}

// =========================================================================================== Stage 3

data class ShotStatus(val section: ReelSection, val footage: List<MediaClip>, val voice: List<MediaClip>, val broll: List<MediaClip>, val needsVoice: Boolean, val needsFootage: Boolean) {
    val footageDone: Boolean get() = !needsFootage || footage.isNotEmpty()
    val voiceDone: Boolean get() = !needsVoice || voice.isNotEmpty()
    val done: Boolean get() = footageDone && voiceDone
}

data class PendingFilmImport(val sectionId: String, val role: ClipRole, val uri: Uri, val clinicalHint: Boolean, val muted: Boolean, val replace: Boolean)

data class FilmUi(val importing: Int? = null, val pending: PendingFilmImport? = null, val error: String? = null, val message: String? = null)

class FilmViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _ui = MutableStateFlow(FilmUi())
    val ui = _ui.asStateFlow()

    fun shots(b: ProjectBundle): List<ShotStatus> = b.sections.sortedBy { it.index }.map { s ->
        val req = b.project.template?.shots?.firstOrNull { it.index == s.index }?.requiredMedia ?: MediaRequirement.TALKING_TO_CAMERA
        val prim = b.clipsFor(s.id, ClipRole.PRIMARY).filter { it.isVisual }
        val voice = b.clipsFor(s.id, ClipRole.VOICEOVER) + b.clipsFor(s.id, ClipRole.PRIMARY).filter { it.kind == MediaKind.AUDIO }
        ShotStatus(s, prim, voice, b.clipsFor(s.id, ClipRole.BROLL), req.needsVoice, req.needsFootage)
    }

    /** Footage for a VO shot is imported muted (dialogue comes from the voiceover). */
    fun picked(section: ReelSection, role: ClipRole, uri: Uri?, voiceShot: Boolean, replace: Boolean) {
        uri ?: return
        val clinical = voiceShot || role == ClipRole.BROLL
        _ui.update { it.copy(pending = PendingFilmImport(section.id, role, uri, clinical, muted = voiceShot && role == ClipRole.PRIMARY, replace = replace)) }
    }

    fun cancelPending() = _ui.update { it.copy(pending = null) }

    fun confirm(clinical: Boolean, identifiable: Boolean, consent: Boolean) {
        val p = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null, importing = bundle.value?.section(p.sectionId)?.index, error = null) }
        viewModelScope.launch {
            runCatching {
                val b = c.projects.getBundle(pid)!!
                val section = b.section(p.sectionId)!!
                val beauty = b.project.template?.let { t -> if (clinical) app.reelify.core.model.BeautySettings() else ProductionPlanner.beautyFor(t, b.project.templateOverrides, section.index, c.settings.current().camera.beauty) }
                    ?: app.reelify.core.model.BeautySettings()
                if (p.replace) b.clipsFor(p.sectionId, p.role).filter { it.isVisual }.forEach { c.projects.deleteClip(pid, it.id) }
                c.importer.import(pid, p.sectionId, p.uri, ImportOptions(p.role, clinical, identifiable, consent, beauty = beauty, muteOriginal = p.muted))
            }.onSuccess { _ui.update { it.copy(importing = null, message = "Added") } }
                .onFailure { e -> _ui.update { it.copy(importing = null, error = e.message ?: "Import failed") } }
        }
    }

    fun removeClip(clip: MediaClip) = viewModelScope.launch { c.projects.deleteClip(pid, clip.id) }
    fun dismiss() = _ui.update { it.copy(error = null, message = null) }

    fun setTeleprompter(index: Int, on: Boolean) = viewModelScope.launch { c.projects.updateTemplateOverrides(pid) { it.withShot(index) { o -> o.copy(teleprompter = on) } } }
}
