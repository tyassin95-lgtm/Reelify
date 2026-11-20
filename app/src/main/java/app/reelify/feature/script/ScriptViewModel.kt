package app.reelify.feature.script

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.reelify.AppContainer
import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.MediaPlan
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ProjectStatus
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ShotType
import app.reelify.core.policy.ClaimChecker
import app.reelify.feature.create.directorContext
import app.reelify.integration.openai.SectionProposal
import app.reelify.integration.openai.SectionTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScriptUi(
    val busy: String? = null,
    val busySection: String? = null,
    val error: String? = null,
    val proposal: SectionProposal? = null,
    val proposalTask: SectionTask = SectionTask.REWRITE,
    val aiFlags: List<ClaimFlag> = emptyList(),
    val claimReviewDone: Boolean = false,
)

class ScriptViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val timing = c.timingState
    private val _ui = MutableStateFlow(ScriptUi())
    val ui = _ui.asStateFlow()
    private var job: Job? = null

    fun localFlags(b: ProjectBundle): List<ClaimFlag> {
        val forbidden = mutableListOf<String>()
        forbidden += b.project.concept.excludedClaims
        return b.sections.flatMap { s -> ClaimChecker.check(s.spokenScript + "\n" + s.onScreenText, s.id, forbidden) }
    }

    private fun run(label: String, sectionId: String? = null, block: suspend () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(busy = label, busySection = sectionId, error = null) }
            try { block(); _ui.update { it.copy(busy = null, busySection = null) } }
            catch (e: CancellationException) { _ui.update { it.copy(busy = null, busySection = null) }; throw e }
            catch (e: Exception) { _ui.update { it.copy(busy = null, busySection = null, error = e.message ?: "Something went wrong") } }
        }
    }

    fun cancelWork() { job?.cancel() }
    fun dismissError() = _ui.update { it.copy(error = null) }

    fun generatePlan(instruction: String? = null) = run("Writing your three-section plan…") {
        val b = c.projects.getBundle(pid)!!
        val plan = c.director.generatePlan(c.directorContext(b.project), b.sections, instruction)
        c.projects.applyPlan(pid, plan.meta, plan.sections, "regenerate plan", respectLocks = true)
        _ui.update { it.copy(aiFlags = emptyList(), claimReviewDone = false) }
    }

    /** Requests a structured replacement for ONE section; nothing is saved until the user accepts. */
    fun regenerate(sectionId: String, instruction: String, task: SectionTask = SectionTask.REWRITE) = run("Rewriting section…", sectionId) {
        val b = c.projects.getBundle(pid)!!
        val proposal = c.director.regenerateSection(c.directorContext(b.project), b.sections, sectionId, instruction.ifBlank { task.label }, task)
        _ui.update { it.copy(proposal = proposal, proposalTask = task) }
    }

    fun acceptProposal() {
        val p = _ui.value.proposal ?: return
        viewModelScope.launch {
            c.projects.replaceSection(pid, p.after, "AI: ${p.instruction}".take(200))
            _ui.update { it.copy(proposal = null) }
        }
    }

    fun retryProposal() { _ui.value.proposal?.let { regenerate(it.before.id, it.instruction, _ui.value.proposalTask) } }
    fun cancelProposal() = _ui.update { it.copy(proposal = null) }

    fun saveSection(section: ReelSection, reason: String = "manual edit") = viewModelScope.launch { c.projects.replaceSection(pid, section, reason) }

    fun toggleLock(section: ReelSection) = viewModelScope.launch { c.projects.updateSectionQuiet(pid, section.id) { it.copy(locked = !it.locked) } }

    fun changeShot(section: ReelSection, shot: ShotType) = viewModelScope.launch {
        c.projects.replaceSection(pid, section.copy(shotType = shot, mediaPlan = MediaPlan.defaultFor(shot), isVoiceover = shot == ShotType.VOICEOVER_BROLL), "shot type → ${shot.label}")
    }

    fun undo(section: ReelSection) = viewModelScope.launch { c.projects.undoSection(pid, section.id) }

    suspend fun revisionCount(sectionId: String) = c.projects.revisionCount(sectionId)

    fun reviewClaims() = run("Reviewing claims…") {
        val b = c.projects.getBundle(pid)!!
        val flags = c.director.reviewClaims(c.directorContext(b.project), b.sections)
        _ui.update { it.copy(aiFlags = flags, claimReviewDone = true) }
    }

    fun updatePlanMeta(t: (PlanMeta) -> PlanMeta) = viewModelScope.launch { c.projects.update(pid) { p -> p.copy(plan = t(p.plan ?: PlanMeta())) } }

    fun approve(onDone: () -> Unit) = viewModelScope.launch {
        c.projects.update(pid) { it.copy(planApproved = true, status = if (it.status == ProjectStatus.DRAFT || it.status == ProjectStatus.SCRIPTED) ProjectStatus.FILMING else it.status) }
        onDone()
    }
}
