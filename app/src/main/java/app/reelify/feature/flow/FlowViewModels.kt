package app.reelify.feature.flow

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.content.ClinicalKnowledgeManager
import app.reelify.core.content.CreativeHistoryRecord
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.FactualBriefBuilder
import app.reelify.core.content.IdeaGenerationMode
import app.reelify.core.content.TopicLibrary
import app.reelify.core.content.TopicOrigin
import app.reelify.core.content.TopicSuggestion
import app.reelify.core.content.TopicUiCategory
import app.reelify.core.content.TreatmentKnowledgeCatalog
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
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SocialPlatform
import app.reelify.core.model.Treatment
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ProductionMode
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.SectionScript
import app.reelify.core.template.ShotRole
import app.reelify.core.util.Ids
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Builds the scriptwriter context preserving full selected CreativeTopic & local creative history. */
suspend fun AppContainer.scriptContext(
    p: Project,
    topicTitle: String,
    brief: String = "",
    idea: String = "",
    selectedTopic: CreativeTopic? = null,
    recentHistory: List<CreativeHistoryRecord> = emptyList(),
): ScriptContext {
    val template = p.template ?: error("Not a template project")
    val treatment = library.getTreatment(p.treatmentId)
    val pack = TreatmentKnowledgeCatalog.packFor(
        treatmentId = treatment?.id ?: p.treatmentId,
        linkedReferenceId = treatment?.linkedReferenceProfileId,
        needsConfiguration = treatment?.needsConfiguration == true,
    )
    val arabic = p.language == ReelLanguage.IRAQI_ARABIC
    val matchedTopic = selectedTopic?.let { sel ->
        TopicLibrary.topicsFor(treatment, template.id).firstOrNull { it.key == sel.id }
    } ?: TopicLibrary.topicsFor(treatment, template.id)
        .firstOrNull { it.title.en == topicTitle || it.title.ar == topicTitle }
    val resolvedTopic = selectedTopic ?: p.script?.creativeTopic
    val factualBrief = FactualBriefBuilder.build(
        treatment = treatment,
        pack = pack,
        topic = matchedTopic,
        selectedTopic = resolvedTopic,
        topicTitle = topicTitle,
        topicBrief = brief,
        userIdea = idea,
        arabic = arabic,
    )
    val history = recentHistory.ifEmpty { content.recentCreativeHistory(p.treatmentId) }
    return ScriptContext(
        language = p.language,
        duration = p.duration,
        template = template,
        treatment = treatment,
        terminology = library.terminology(),
        brand = settings.currentBrand(),
        topicTitle = topicTitle,
        topicBrief = brief,
        userIdea = idea,
        concept = p.concept,
        recentHooks = content.recentHooks(),
        recentTopics = content.recentTopicKeys(p.treatmentId),
        examples = if (arabic) content.iraqiExamplesForPrompt() else emptyList(),
        factualBrief = factualBrief,
        selectedTopic = resolvedTopic,
        recentHistory = history,
    )
}

// =========================================================================================== Stage 1

class ChooseReelViewModel(private val c: AppContainer) : ViewModel() {
    val treatments = c.library.observeTreatments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val templates = c.templates.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun last(): LastReelChoice = c.settings.current().lastChoice

    /**
     * Starts an EPHEMERAL creation session without creating a persistent Room project yet.
     * Preferences are remembered for convenience, while Room stays clean until script success.
     */
    suspend fun startSession(
        language: ReelLanguage,
        treatment: Treatment,
        duration: ReelDuration,
        template: ReelTemplate,
    ): CreationDraftSession {
        c.settings.update { it.copy(lastChoice = LastReelChoice(treatment.id, language, duration.totalMs, template.id)) }
        val session = CreationDraftSession(
            sessionId = Ids.new(),
            treatmentId = treatment.id,
            templateId = template.id,
            language = language,
            duration = duration,
        )
        c.creationSessions[session.sessionId] = session
        return session
    }
}

// =========================================================================================== Stage 2

data class MessageUi(
    val project: Project? = null,
    val treatment: Treatment? = null,
    val isEphemeralSession: Boolean = true,
    val suggestions: List<TopicSuggestion> = emptyList(),
    val selectedCategory: TopicUiCategory = TopicUiCategory.ALL,
    val visibleCount: Int = 8,
    val ideas: List<CreativeTopic> = emptyList(),
    val savedIdeas: List<CreativeTopic> = emptyList(),
    val ideasLoading: Boolean = false,
    val generating: String? = null,
    val generatingKey: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val concept: ReelConcept = ReelConcept(),
    val profileReady: Boolean = false,
)

class MessageViewModel(
    private val c: AppContainer,
    private val pidOrDraftKey: String,
    private val arabicUi: Boolean,
    private val fallbackTreatmentId: String = "",
    private val fallbackTemplateId: String = "",
    private val fallbackLangCode: String = "",
    private val fallbackDurationMs: Long = 30_000L,
) : ViewModel() {
    private val _ui = MutableStateFlow(MessageUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null
    private var saveJob: Job? = null
    private var recentKeys: List<String> = emptyList()
    private var ephemeralSession: CreationDraftSession? = null
    private var persistedProjectId: String? = null

    init {
        viewModelScope.launch {
            val isDraft = pidOrDraftKey.startsWith("draft_")
            var project: Project? = null
            if (isDraft) {
                val sid = pidOrDraftKey.removePrefix("draft_")
                val existing = c.creationSessions[sid]
                val session = existing ?: CreationDraftSession(
                    sessionId = sid,
                    treatmentId = fallbackTreatmentId.ifBlank { "aerolase" },
                    templateId = fallbackTemplateId.ifBlank { "expert_explains" },
                    language = ReelLanguage.fromCode(fallbackLangCode.ifBlank { c.settings.current().defaultReelLanguage.code }),
                    duration = ReelDuration.fromMs(fallbackDurationMs),
                ).also { c.creationSessions[sid] = it }

                if (session.persistedProjectId != null) {
                    persistedProjectId = session.persistedProjectId
                    project = c.projects.getProject(session.persistedProjectId)
                }
                if (project == null) {
                    ephemeralSession = session
                    val tpl = c.templates.get(session.templateId) ?: app.reelify.core.template.BuiltInTemplates.EXPERT_EXPLAINS
                    val s = c.settings.current()
                    val t = c.library.getTreatment(session.treatmentId)
                    val titlePrefix = if (session.language == ReelLanguage.IRAQI_ARABIC) t?.arabicName.orEmpty().ifBlank { t?.name.orEmpty() } else t?.name.orEmpty()
                    project = Project(
                        id = pidOrDraftKey,
                        title = "$titlePrefix · ${tpl.name.resolve(arabicUi)}",
                        createdAt = 0L,
                        updatedAt = 0L,
                        language = session.language,
                        treatmentId = session.treatmentId,
                        duration = session.duration,
                        style = ReelStyle.EDUCATIONAL,
                        concept = session.concept,
                        mode = ProductionMode.TEMPLATE,
                        template = tpl,
                        edit = ProductionPlanner.templateEdit(tpl, session.duration, EditSettings(captions = CaptionSettings(), quality = s.exportQuality)),
                    )
                }
            } else {
                persistedProjectId = pidOrDraftKey
                project = c.projects.getProject(pidOrDraftKey)
            }

            val p = project ?: return@launch
            val t = c.library.getTreatment(p.treatmentId)
            recentKeys = c.content.recentTopicKeys(p.treatmentId)
            val arabicTitles = p.language == ReelLanguage.IRAQI_ARABIC
            _ui.update {
                it.copy(
                    project = p,
                    treatment = t,
                    isEphemeralSession = persistedProjectId == null,
                    concept = p.concept,
                    profileReady = TopicLibrary.profileReady(t),
                    suggestions = TopicLibrary.suggestions(t, p.template?.id, recentKeys, arabicTitles, uiCategory = it.selectedCategory),
                )
            }
            launch {
                c.content.observeSavedIdeas(p.treatmentId).collect { saved ->
                    _ui.update { st -> st.copy(savedIdeas = saved.filter { it.templateId == p.template?.id }) }
                }
            }
        }
    }

    fun selectCategory(category: TopicUiCategory) {
        val state = _ui.value
        val arabicTitles = state.project?.language == ReelLanguage.IRAQI_ARABIC
        val filtered = TopicLibrary.suggestions(
            treatment = state.treatment,
            templateId = state.project?.template?.id,
            recentKeys = recentKeys,
            arabic = arabicTitles,
            uiCategory = category,
        )
        _ui.update { it.copy(selectedCategory = category, visibleCount = 8, suggestions = filtered) }
    }

    fun showMoreTopics() {
        _ui.update { it.copy(visibleCount = it.visibleCount + 12) }
    }

    fun setPlatform(platform: SocialPlatform) {
        editConcept { it.copy(platform = platform) }
    }

    fun confirmCleopatraIdentity() {
        viewModelScope.launch {
            val updated = c.library.confirmCleopatraAsDiamondGlow() ?: return@launch
            val state = _ui.value
            val arabicTitles = state.project?.language == ReelLanguage.IRAQI_ARABIC
            val refreshed = TopicLibrary.suggestions(
                treatment = updated,
                templateId = state.project?.template?.id,
                recentKeys = recentKeys,
                arabic = arabicTitles,
                uiCategory = state.selectedCategory,
            )
            _ui.update {
                it.copy(
                    treatment = updated,
                    profileReady = TopicLibrary.profileReady(updated),
                    visibleCount = 8,
                    suggestions = refreshed,
                )
            }
        }
    }

    fun editConcept(t: (ReelConcept) -> ReelConcept) {
        val next = t(_ui.value.concept)
        _ui.update { it.copy(concept = next, project = it.project?.copy(concept = next)) }
        ephemeralSession?.let { s ->
            val updated = s.copy(concept = next)
            ephemeralSession = updated
            c.creationSessions[updated.sessionId] = updated
        }
        val realPid = persistedProjectId
        if (realPid != null) {
            saveJob?.cancel()
            saveJob = viewModelScope.launch { delay(350); c.projects.updateConcept(realPid, next) }
        }
    }

    fun surpriseMe(seedTopic: CreativeTopic? = null, mode: IdeaGenerationMode = IdeaGenerationMode.FRESH) {
        val snapshot = _ui.value.project ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(ideasLoading = true, error = null, notice = null) }
            try {
                val p = persistedProjectId?.let { c.projects.getProject(it) } ?: snapshot
                val history = c.content.recentCreativeHistory(p.treatmentId)
                val ctx = c.scriptContext(
                    p = p.copy(concept = _ui.value.concept),
                    topicTitle = seedTopic?.title ?: "",
                    selectedTopic = seedTopic,
                    recentHistory = history,
                )
                val fresh = c.scriptWriter.ideas(ctx, seedTopic = seedTopic, mode = mode)
                _ui.update { it.copy(ideas = fresh, ideasLoading = false) }
            } catch (e: CancellationException) {
                _ui.update { it.copy(ideasLoading = false) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(ideasLoading = false, error = e.message ?: "Couldn't generate fresh clinical ideas") }
            }
        }
    }

    fun saveIdeaForLater(idea: CreativeTopic) {
        viewModelScope.launch {
            c.content.saveIdeaForLater(idea)
            _ui.update { it.copy(notice = "Saved '${idea.title}' for later") }
        }
    }

    fun removeSavedIdea(idea: CreativeTopic) {
        viewModelScope.launch {
            c.content.removeSavedIdea(idea.treatmentId, idea.id)
        }
    }

    fun useTopic(s: TopicSuggestion, onDone: (String) -> Unit) {
        val state = _ui.value
        val p = state.project ?: return
        val tplId = p.template?.id ?: return
        val tr = state.treatment
        val pack = TreatmentKnowledgeCatalog.packFor(tr?.id ?: p.treatmentId, tr?.linkedReferenceProfileId, tr?.needsConfiguration == true)
        val creative = CreativeTopic.fromCuratedSuggestion(s, tr, tplId, pack, p.language == ReelLanguage.IRAQI_ARABIC)
        generate(creative, onDone)
    }

    fun useIdea(idea: CreativeTopic, onDone: (String) -> Unit) = generate(idea, onDone)

    fun useOwnIdea(text: String, onDone: (String) -> Unit) {
        val state = _ui.value
        val p = state.project ?: return
        val tplId = p.template?.id ?: return
        if (text.isBlank()) return
        val creative = CreativeTopic.fromUserInput(state.treatment, tplId, text, p.language == ReelLanguage.IRAQI_ARABIC)
        generate(creative, onDone)
    }

    private fun generate(topic: CreativeTopic, onDone: (String) -> Unit) {
        val snapshot = _ui.value.project ?: return
        val tpl = snapshot.template ?: return
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(generating = topic.title, generatingKey = topic.id, error = null, notice = null) }
            try {
                val updatedConcept = _ui.value.concept.copy(summary = topic.title)
                val workingProject = (persistedProjectId?.let { c.projects.getProject(it) } ?: snapshot).copy(concept = updatedConcept)
                val history = c.content.recentCreativeHistory(workingProject.treatmentId)
                val ctx = c.scriptContext(
                    p = workingProject,
                    topicTitle = topic.title,
                    brief = topic.educationalObjective,
                    idea = if (topic.origin == TopicOrigin.USER) topic.title else "",
                    selectedTopic = topic,
                    recentHistory = history,
                )
                val preferredHook = if (topic.origin == TopicOrigin.AI && topic.hookAngle.isNotBlank()) topic.hookAngle else null
                val written = c.scriptWriter.generate(ctx, topic.id, preferredHook)

                val finalMeta = if (c.settings.current().aiEditorialReview) {
                    runCatching {
                        val tmpSections = ProductionPlanner.sections(tpl, emptyList(), { "tmp" }, 0L).let {
                            ProductionPlanner.mergeScript(tpl, it, written.scripts, false, null, 0L)
                        }
                        val review = c.scriptWriter.editorialReview(ctx, tmpSections)
                        written.meta.copy(
                            qualityNotes = written.meta.qualityNotes +
                                review.issues.map { "Shot ${it.sectionIndex}: ${it.problem} → ${it.suggestion}" } +
                                "Editorial review (AI, advisory): naturalness ${review.naturalness}/5, engagement ${review.engagement}/5",
                        )
                    }.getOrElse { if (it is CancellationException) throw it; written.meta }
                } else {
                    written.meta
                }

                val targetPid: String = persistedProjectId?.also { existingId ->
                    c.projects.updateConcept(existingId, updatedConcept)
                    c.projects.applyScript(existingId, finalMeta, written.scripts, "AI script", respectLocks = true, title = written.title)
                } ?: run {
                    val s = c.settings.current()
                    val baseEdit = EditSettings(captions = CaptionSettings(), quality = s.exportQuality)
                    val bundle = c.projects.createFromTemplateWithScriptAtomically(
                        language = workingProject.language,
                        treatmentId = workingProject.treatmentId,
                        duration = workingProject.duration,
                        template = tpl,
                        title = written.title.ifBlank { workingProject.title },
                        baseEdit = baseEdit,
                        concept = updatedConcept,
                        meta = finalMeta,
                        scripts = written.scripts,
                        reason = "AI script",
                    )
                    persistedProjectId = bundle.project.id
                    ephemeralSession?.let { sess ->
                        val updatedSess = sess.copy(concept = updatedConcept, selectedTopic = topic, persistedProjectId = bundle.project.id)
                        ephemeralSession = updatedSess
                        c.creationSessions[updatedSess.sessionId] = updatedSess
                    }
                    bundle.project.id
                }

                c.content.recordCreativeTopicUse(topic, written.meta.hookOptions.firstOrNull()?.text.orEmpty(), approvedByUser = true)
                _ui.update { it.copy(generating = null, generatingKey = null, isEphemeralSession = false) }
                onDone(targetPid)
            } catch (e: CancellationException) {
                _ui.update { it.copy(generating = null, generatingKey = null) }
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(generating = null, generatingKey = null, error = e.message ?: "Script generation failed") }
            }
        }
    }

    fun cancel() { job?.cancel() }
    fun dismissError() = _ui.update { it.copy(error = null, notice = null) }
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

    /** Explicitly normalizes medical terminology to canonical English Latin script across unlocked sections at user request. */
    fun normalizeSavedMedicalTerminology() = viewModelScope.launch {
        val b = c.projects.getBundle(pid) ?: return@launch
        val meta = b.project.script ?: app.reelify.core.model.ScriptMeta()
        val terms = c.library.terminology()
        val updated = b.sections.filter { !it.locked }.map { s ->
            SectionScript(
                index = s.index,
                spokenScript = app.reelify.core.content.MedicalGlossary.enforceEnglishMedicalTerms(s.spokenScript, terms).text,
                onScreenHeadline = app.reelify.core.content.MedicalGlossary.enforceEnglishMedicalTerms(s.onScreenText, terms).text,
                emphasisWords = s.captionEmphasis,
            )
        }
        val normalizedHooks = meta.hookOptions.map {
            it.copy(text = app.reelify.core.content.MedicalGlossary.enforceEnglishMedicalTerms(it.text, terms).text)
        }
        val normalizedActiveHook = app.reelify.core.content.MedicalGlossary.enforceEnglishMedicalTerms(meta.activeHook, terms).text
        c.projects.applyScript(
            pid,
            meta.copy(hookOptions = normalizedHooks, activeHook = normalizedActiveHook),
            updated,
            "normalize medical terminology",
            respectLocks = true,
        )
        _ui.update { it.copy(summary = "Normalized medical terminology to English across unlocked shots.") }
    }

    fun saveToMyVoice(spoken: String) = viewModelScope.launch {
        val clean = app.reelify.core.content.GeneratedContentNormalizer.clean(spoken).trim()
        if (clean.isBlank()) return@launch
        c.content.saveOwnerVoiceSample(clean)
        _ui.update { it.copy(summary = "Saved wording to your voice style guide") }
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
