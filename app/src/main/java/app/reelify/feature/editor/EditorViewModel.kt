package app.reelify.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.transformer.Composition
import app.reelify.AppContainer
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.ResolvedTimeline
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MediaClip
import app.reelify.core.model.ProjectBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

data class EditorUi(
    val composition: Composition? = null,
    val timeline: ResolvedTimeline? = null,
    val selectedClip: String? = null,
    val selectedCue: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: String? = null,
)

class EditorViewModel(private val c: AppContainer, private val pid: String) : ViewModel() {
    val bundle: StateFlow<ProjectBundle?> = c.projects.observeBundle(pid).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _ui = MutableStateFlow(EditorUi())
    val ui = _ui.asStateFlow()
    private val history = UndoStack()

    init {
        viewModelScope.launch {
            bundle.filterNotNull().distinctUntilChanged { a, b -> a.clips == b.clips && a.project.edit == b.project.edit && a.project.captions == b.project.captions }.collectLatest { rebuild() }
        }
    }

    private suspend fun rebuild() {
        runCatching {
            withContext(Dispatchers.Default) {
                val inputs = c.preparer.inputs(pid, proxy = c.settings.current().proxyPreview)
                inputs.timeline to if (inputs.timeline.video.isEmpty()) null else c.compositionFactory.build(inputs, RenderPurpose.PREVIEW).composition
            }
        }.onSuccess { (tl, comp) -> _ui.update { it.copy(timeline = tl, composition = comp) } }
            .onFailure { e -> if (e is CancellationException) throw e; _ui.update { it.copy(error = e.message) } }
    }

    private fun snapshot(): EditorSnapshot? = bundle.value?.let { EditorSnapshot(it.clips, it.project.edit) }

    /** Applies a change as one undoable step and persists it (autosave). */
    private fun commit(clips: (List<MediaClip>) -> List<MediaClip> = { it }, edit: (EditSettings) -> EditSettings = { it }) {
        val before = snapshot() ?: return
        val newClips = clips(before.clips)
        val newEdit = edit(before.edit)
        if (newClips == before.clips && newEdit == before.edit) return
        history.push(before)
        viewModelScope.launch { apply(EditorSnapshot(newClips, newEdit)) }
    }

    private suspend fun apply(s: EditorSnapshot) {
        c.projects.replaceClips(pid, s.clips)
        c.projects.updateEdit(pid) { s.edit }
        _ui.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    fun undo() { val cur = snapshot() ?: return; history.undo(cur)?.let { viewModelScope.launch { apply(it) } } }
    fun redo() { val cur = snapshot() ?: return; history.redo(cur)?.let { viewModelScope.launch { apply(it) } } }

    fun select(clipId: String?) = _ui.update { it.copy(selectedClip = clipId, selectedCue = null) }
    fun selectCue(id: String?) = _ui.update { it.copy(selectedCue = id, selectedClip = null) }

    fun splitAt(timelineMs: Long) {
        val tl = _ui.value.timeline ?: return
        val seg = TimelineOps.primaryAt(tl, timelineMs) ?: return
        val clip = seg.clip ?: return
        commit(clips = { TimelineOps.split(it, clip.id, seg.sourceStartMs + (timelineMs - seg.timelineStartMs)) })
    }

    fun trimSelected(deltaStart: Long, deltaEnd: Long) {
        val id = _ui.value.selectedClip ?: return
        val c0 = bundle.value?.clips?.firstOrNull { it.id == id } ?: return
        commit(clips = { TimelineOps.trim(it, id, c0.trimStartMs + deltaStart, c0.trimEndMs + deltaEnd) })
    }

    fun setTrimEnd(clipId: String, endMs: Long) = commit(clips = { TimelineOps.trim(it, clipId, null, endMs) })
    fun reorderSelected(delta: Int) { _ui.value.selectedClip?.let { id -> commit(clips = { TimelineOps.reorder(it, id, delta) }) } }
    fun deleteSelected() { _ui.value.selectedClip?.let { id -> commit(clips = { TimelineOps.delete(it, id) }); select(null) } }
    fun duplicateSelected() { _ui.value.selectedClip?.let { id -> commit(clips = { TimelineOps.duplicate(it, id) }) } }
    fun toggleMuteSelected() { _ui.value.selectedClip?.let { id -> commit(clips = { l -> l.map { if (it.id == id) it.copy(muted = !it.muted) else it } }) } }

    fun addSfx(sfxId: String, atMs: Long) = commit(edit = { TimelineOps.addSfx(it, sfxId, atMs) })
    fun moveCue(id: String, atMs: Long) {
        val tl = _ui.value.timeline ?: return
        val snapped = TimelineOps.snap(atMs, TimelineOps.markers(tl), 150)
        commit(edit = { TimelineOps.moveSfx(it, id, snapped, tl.totalMs) })
    }
    fun deleteCue() { _ui.value.selectedCue?.let { id -> commit(edit = { TimelineOps.removeSfx(it, id) }); selectCue(null) } }
    fun dismissError() = _ui.update { it.copy(error = null) }
}
