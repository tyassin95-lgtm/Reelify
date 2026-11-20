package app.reelify.core.data

import app.reelify.core.database.ClipEntity
import app.reelify.core.database.ProjectDao
import app.reelify.core.database.ProjectEntity
import app.reelify.core.database.SectionEntity
import app.reelify.core.database.SectionRevisionEntity
import app.reelify.core.model.CaptionTrack
import app.reelify.core.model.ClipRole
import app.reelify.core.model.CreativeFeedback
import app.reelify.core.model.EditSettings
import app.reelify.core.model.ExportInfo
import app.reelify.core.model.MediaClip
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ProjectStatus
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.SectionRevision
import app.reelify.core.model.BrandProfile
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.Ids
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single source of truth for projects, their three stable sections, revision history and media
 * assignments. All writes are serialized to avoid lost updates from concurrent autosaves.
 */
class ProjectRepository(
    private val dao: ProjectDao,
    private val clock: Clock = Clock.System,
    private val onProjectDeleted: suspend (String) -> Unit = {},
) {
    private val writeLock = Mutex()

    fun observeProjects(): Flow<List<Project>> = dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    fun observeBundle(id: String): Flow<ProjectBundle?> =
        combine(dao.observe(id), dao.observeSections(id), dao.observeClips(id)) { p, s, c ->
            val project = p?.toDomain() ?: return@combine null
            ProjectBundle(project, s.mapNotNull { it.toDomain() }.sortedBy { it.index }, c.mapNotNull { it.toDomain() })
        }

    suspend fun getBundle(id: String): ProjectBundle? {
        val project = dao.get(id)?.toDomain() ?: return null
        return ProjectBundle(
            project,
            dao.getSections(id).mapNotNull { it.toDomain() }.sortedBy { it.index },
            dao.getClips(id).mapNotNull { it.toDomain() },
        )
    }

    suspend fun getProject(id: String): Project? = dao.get(id)?.toDomain()

    suspend fun create(
        language: ReelLanguage,
        treatmentId: String,
        duration: ReelDuration,
        style: ReelStyle,
        title: String,
        edit: EditSettings = EditSettings(),
    ): Project = writeLock.withLock {
        val now = clock.now()
        val project = Project(
            id = Ids.new(), title = title, createdAt = now, updatedAt = now, language = language,
            treatmentId = treatmentId, duration = duration, style = style, edit = edit,
        )
        dao.upsert(project.toEntity())
        val purposes = listOf(SectionPurpose.HOOK, SectionPurpose.EXPLANATION, SectionPurpose.CALL_TO_ACTION)
        dao.upsertSections(purposes.mapIndexed { i, p ->
            ReelSection(id = "sec-" + Ids.new(), index = i + 1, purpose = p, updatedAt = now).toEntity(project.id)
        })
        project
    }

    /** Read-modify-write of the project row. Returns the updated project or null if missing. */
    suspend fun update(id: String, transform: (Project) -> Project): Project? = writeLock.withLock {
        val current = dao.get(id)?.toDomain() ?: return null
        val updated = transform(current).copy(id = current.id, createdAt = current.createdAt, updatedAt = clock.now())
        dao.upsert(updated.toEntity())
        updated
    }

    suspend fun updateConcept(id: String, concept: ReelConcept) = update(id) { it.copy(concept = concept) }
    suspend fun updateFeedback(id: String, feedback: CreativeFeedback?) = update(id) { it.copy(feedback = feedback) }
    suspend fun updateEdit(id: String, transform: (EditSettings) -> EditSettings) =
        update(id) { p -> p.copy(edit = transform(p.edit).let { e -> e.copy(editVersion = p.edit.editVersion + 1) }) }
    suspend fun updateCaptions(id: String, captions: CaptionTrack?) = update(id) { it.copy(captions = captions) }
    suspend fun updateExport(id: String, export: ExportInfo?) =
        update(id) { it.copy(export = export, status = if (export?.path != null) ProjectStatus.EXPORTED else it.status) }

    suspend fun applyPlan(
        id: String,
        plan: PlanMeta,
        sections: List<ReelSection>,
        reason: String,
        respectLocks: Boolean = true,
    ): ProjectBundle? = writeLock.withLock {
        val project = dao.get(id)?.toDomain() ?: return null
        val existing = dao.getSections(id).mapNotNull { it.toDomain() }.associateBy { it.index }
        val now = clock.now()
        val merged = sections.sortedBy { it.index }.map { incoming ->
            val old = existing[incoming.index]
            when {
                old == null -> incoming.copy(updatedAt = now)
                respectLocks && old.locked -> old // never overwrite a locked/approved section
                else -> {
                    if (old.spokenScript.isNotBlank() || old.title.isNotBlank()) snapshot(id, old, reason)
                    incoming.copy(id = old.id, locked = old.locked, updatedAt = now)
                }
            }
        }
        dao.upsertSections(merged.map { it.toEntity(id) })
        val updated = project.copy(
            plan = plan,
            title = plan.title.ifBlank { project.title },
            status = if (project.status == ProjectStatus.DRAFT) ProjectStatus.SCRIPTED else project.status,
            updatedAt = now,
        )
        dao.upsert(updated.toEntity())
        ProjectBundle(updated, merged, dao.getClips(id).mapNotNull { it.toDomain() })
    }

    /** Replace exactly one section (selective regeneration / manual edit) keeping its stable id. */
    suspend fun replaceSection(projectId: String, section: ReelSection, reason: String): ReelSection = writeLock.withLock {
        val old = dao.getSection(section.id)?.toDomain()
            ?: error("Section ${section.id} not found")
        snapshot(projectId, old, reason)
        val saved = section.copy(id = old.id, index = old.index, updatedAt = clock.now())
        dao.upsertSections(listOf(saved.toEntity(projectId)))
        touch(projectId)
        saved
    }

    /** Update a section without recording a revision (e.g. toggling lock or media plan). */
    suspend fun updateSectionQuiet(projectId: String, sectionId: String, transform: (ReelSection) -> ReelSection) = writeLock.withLock {
        val old = dao.getSection(sectionId)?.toDomain() ?: return@withLock null
        val saved = transform(old).copy(id = old.id, index = old.index, updatedAt = clock.now())
        dao.upsertSections(listOf(saved.toEntity(projectId)))
        touch(projectId)
        saved
    }

    fun observeRevisions(sectionId: String): Flow<List<SectionRevision>> =
        dao.observeRevisions(sectionId).map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun revisionCount(sectionId: String): Int = dao.revisionCount(sectionId)

    /** Restore the most recent revision of a section (undo). Returns the restored section. */
    suspend fun undoSection(projectId: String, sectionId: String): ReelSection? = writeLock.withLock {
        val rev = dao.latestRevision(sectionId) ?: return null
        val snap = AppJson.decodeOrNull<ReelSection>(rev.snapshotJson) ?: return null
        val restored = snap.copy(updatedAt = clock.now())
        dao.upsertSections(listOf(restored.toEntity(projectId)))
        dao.deleteRevision(rev.id)
        touch(projectId)
        restored
    }

    suspend fun upsertClip(clip: MediaClip) = writeLock.withLock {
        dao.upsertClips(listOf(clip.toEntity()))
        touch(clip.projectId)
    }

    suspend fun upsertClips(projectId: String, clips: List<MediaClip>) = writeLock.withLock {
        if (clips.isNotEmpty()) dao.upsertClips(clips.map { it.toEntity() })
        touch(projectId)
    }

    suspend fun getClip(id: String): MediaClip? = dao.getClip(id)?.toDomain()

    suspend fun allClips(): List<MediaClip> = dao.getAllClips().mapNotNull { it.toDomain() }

    suspend fun deleteClip(projectId: String, clipId: String) = writeLock.withLock {
        dao.deleteClip(clipId)
        touch(projectId)
    }

    /** Atomically replace the full clip set (used by the timeline editor with undo/redo). */
    suspend fun replaceClips(projectId: String, clips: List<MediaClip>) = writeLock.withLock {
        dao.replaceClips(projectId, clips.map { it.toEntity() })
        touch(projectId)
    }

    suspend fun nextOrder(projectId: String, sectionId: String, role: ClipRole): Int =
        dao.getClips(projectId).mapNotNull { it.toDomain() }
            .filter { it.sectionId == sectionId && it.role == role }
            .maxOfOrNull { it.order + 1 } ?: 0

    suspend fun delete(id: String) = writeLock.withLock {
        dao.delete(id)
        onProjectDeleted(id)
    }

    suspend fun duplicateAsTemplate(id: String): Project? {
        val bundle = getBundle(id) ?: return null
        val p = bundle.project
        val created = create(p.language, p.treatmentId, p.duration, p.style, p.title + " (copy)", p.edit.copy(editVersion = 0))
        update(created.id) { it.copy(concept = p.concept, feedback = p.feedback) }
        bundle.project.plan?.let { plan ->
            applyPlan(created.id, plan, bundle.sections.map { it.copy(locked = false) }, "duplicate", respectLocks = false)
        }
        return getProject(created.id)
    }

    private suspend fun snapshot(projectId: String, section: ReelSection, reason: String) {
        dao.insertRevision(
            SectionRevisionEntity(
                sectionId = section.id, projectId = projectId, createdAt = clock.now(), reason = reason,
                snapshotJson = AppJson.encodeToString(ReelSection.serializer(), section),
            ),
        )
    }

    private suspend fun touch(projectId: String) {
        dao.get(projectId)?.let { dao.upsert(it.copy(updatedAt = clock.now())) }
    }
}

// ---------------------------------------------------------------- mapping

internal fun Project.toEntity() = ProjectEntity(
    id = id, title = title, createdAt = createdAt, updatedAt = updatedAt, language = language.code,
    treatmentId = treatmentId, durationMs = duration.totalMs, style = style.wire, status = status.name,
    planApproved = planApproved, thumbnailPath = thumbnailPath,
    conceptJson = AppJson.encodeToString(ReelConcept.serializer(), concept),
    feedbackJson = feedback?.let { AppJson.encodeToString(CreativeFeedback.serializer(), it) },
    planJson = plan?.let { AppJson.encodeToString(PlanMeta.serializer(), it) },
    editJson = AppJson.encodeToString(EditSettings.serializer(), edit),
    captionsJson = captions?.let { AppJson.encodeToString(CaptionTrack.serializer(), it) },
    brandJson = brandSnapshot?.let { AppJson.encodeToString(BrandProfile.serializer(), it) },
    exportJson = export?.let { AppJson.encodeToString(ExportInfo.serializer(), it) },
)

internal fun ProjectEntity.toDomain(): Project? = runCatching {
    Project(
        id = id, title = title, createdAt = createdAt, updatedAt = updatedAt,
        language = ReelLanguage.fromCode(language), treatmentId = treatmentId,
        duration = ReelDuration.fromMs(durationMs), style = ReelStyle.fromWire(style),
        concept = AppJson.decodeOrNull<ReelConcept>(conceptJson) ?: ReelConcept(),
        feedback = AppJson.decodeOrNull<CreativeFeedback>(feedbackJson),
        plan = AppJson.decodeOrNull<PlanMeta>(planJson),
        planApproved = planApproved,
        status = runCatching { ProjectStatus.valueOf(status) }.getOrDefault(ProjectStatus.DRAFT),
        edit = AppJson.decodeOrNull<EditSettings>(editJson) ?: EditSettings(),
        captions = AppJson.decodeOrNull<CaptionTrack>(captionsJson),
        brandSnapshot = AppJson.decodeOrNull<BrandProfile>(brandJson),
        export = AppJson.decodeOrNull<ExportInfo>(exportJson),
        thumbnailPath = thumbnailPath,
    )
}.getOrNull()

internal fun ReelSection.toEntity(projectId: String) = SectionEntity(
    id = id, projectId = projectId, sectionIndex = index, locked = locked, updatedAt = updatedAt,
    dataJson = AppJson.encodeToString(ReelSection.serializer(), this),
)

internal fun SectionEntity.toDomain(): ReelSection? =
    AppJson.decodeOrNull<ReelSection>(dataJson)?.copy(id = id, index = sectionIndex, locked = locked)

internal fun SectionRevisionEntity.toDomain(): SectionRevision? =
    AppJson.decodeOrNull<ReelSection>(snapshotJson)?.let { SectionRevision(id, sectionId, createdAt, reason, it) }

internal fun MediaClip.toEntity() = ClipEntity(
    id = id, projectId = projectId, sectionId = sectionId, role = role.name, ordering = order, uri = uri,
    createdAt = createdAt, dataJson = AppJson.encodeToString(MediaClip.serializer(), this),
)

internal fun ClipEntity.toDomain(): MediaClip? = AppJson.decodeOrNull<MediaClip>(dataJson)
