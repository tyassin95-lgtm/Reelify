package app.reelify

import app.reelify.core.model.ClipOrigin
import app.reelify.core.model.ClipRole
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType

object Fixtures {
    fun project(duration: ReelDuration = ReelDuration.S30, language: ReelLanguage = ReelLanguage.IRAQI_ARABIC) =
        Project("p1", "Test", 0, 0, language, "aerolase", duration, ReelStyle.EDUCATIONAL)

    fun sections() = listOf(
        ReelSection("s1", 1, SectionPurpose.HOOK, spokenScript = "hook", shotType = ShotType.TALKING_HEAD),
        ReelSection("s2", 2, SectionPurpose.EXPLANATION, spokenScript = "explain", shotType = ShotType.TREATMENT_BROLL),
        ReelSection("s3", 3, SectionPurpose.CALL_TO_ACTION, spokenScript = "cta", shotType = ShotType.TALKING_HEAD),
    )

    fun clip(
        id: String, section: String, role: ClipRole = ClipRole.PRIMARY, kind: MediaKind = MediaKind.VIDEO,
        source: Long = 10_000, trimStart: Long = 0, trimEnd: Long = source, order: Int = 0, hasAudio: Boolean = true,
        placement: Long? = null,
    ) = MediaClip(id, "p1", section, role, order, kind, ClipOrigin.CAMERA, "/tmp/$id.mp4", id, source, 1080, 1920, 0, 30f, hasAudio,
        trimStartMs = trimStart, trimEndMs = trimEnd, placementMs = placement)

    fun bundle(clips: List<MediaClip>, duration: ReelDuration = ReelDuration.S30, project: Project = project(duration)) =
        ProjectBundle(project, sections(), clips)
}
