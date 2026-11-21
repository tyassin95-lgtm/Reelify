package app.reelify.core.model

import kotlinx.serialization.Serializable

/** The user's original idea and optional notes (Screen 4). */
@Serializable
data class ReelConcept(
    val summary: String = "",
    val desiredMessage: String = "",
    val audience: String = "",
    val clinicalPoints: String = "",
    val footageAvailability: String = "",
    val tone: String = "",
    val cta: String = "",
    val mustInclude: List<String> = emptyList(),
    val excludedClaims: List<String> = emptyList(),
)

@Serializable
enum class ClaimSeverity { INFO, WARNING, BLOCKER }

@Serializable
data class ClaimFlag(
    val claim: String,
    val issue: String,
    val suggestion: String,
    val severity: ClaimSeverity = ClaimSeverity.WARNING,
    val sectionId: String? = null,
    val source: String = "ai",
)

/** AI interpretation of the concept (Screen 5). All fields are user-editable. */
@Serializable
data class CreativeFeedback(
    val interpretation: String = "",
    val creativeAngle: String = "",
    val targetAudience: String = "",
    val hook: String = "",
    val alternativeHooks: List<String> = emptyList(),
    val mainMessage: String = "",
    val recommendedStyle: ReelStyle = ReelStyle.EDUCATIONAL,
    val visualApproach: String = "",
    val improvements: List<String> = emptyList(),
    val cautions: List<ClaimFlag> = emptyList(),
    val approved: Boolean = false,
    val createdAt: Long = 0,
)

/** Plan-level metadata of the approved script. */
@Serializable
data class PlanMeta(
    val title: String = "",
    val targetAudience: String = "",
    val purpose: String = "",
    val creativeStyle: String = "",
    val hook: String = "",
    val musicMood: MusicMood = MusicMood.ELEGANT,
    val sfxNotes: String = "",
    val cta: String = "",
    val editingNotes: String = "",
    val claimFlags: List<ClaimFlag> = emptyList(),
    val promptVersion: String = "",
    val model: String = "",
)

/** One of exactly three main creative units. [id] is stable for the lifetime of the project. */
@Serializable
data class ReelSection(
    val id: String,
    val index: Int,
    val purpose: SectionPurpose = SectionPurpose.HOOK,
    val title: String = "",
    val spokenScript: String = "",
    val isVoiceover: Boolean = false,
    val shotType: ShotType = ShotType.TALKING_HEAD,
    val cameraDirection: String = "",
    val framing: String = "",
    val lightingTip: String = "",
    val deliveryDirection: String = "",
    val bRollSuggestion: String = "",
    val onScreenText: String = "",
    val captionEmphasis: List<String> = emptyList(),
    val sfxCue: String = "",
    val transitionOut: TransitionType = TransitionType.CUT,
    val editNotes: String = "",
    val mediaPlan: MediaPlan = MediaPlan.RECORD_TALKING,
    val locked: Boolean = false,
    val updatedAt: Long = 0,
    /** Template shot role (Reelify 2.0). Null for legacy sections. */
    val role: String? = null,
)

@Serializable
data class SectionRevision(
    val id: Long,
    val sectionId: String,
    val createdAt: Long,
    val reason: String,
    val snapshot: ReelSection,
)

/** Non-destructive reframing applied to a clip after 9:16 crop-to-fill. */
@Serializable
data class ClipTransform(
    val zoom: Float = 1f,          // >= 1
    val panX: Float = 0f,          // -1..1 (fraction of available travel)
    val panY: Float = 0f,
    val rotation: Int = 0,         // 0/90/180/270 user rotation
    val flipHorizontal: Boolean = false,
    val kenBurns: Boolean = false, // slow push for stills
)

@Serializable
enum class BeautyPreset(val label: String) { NATURAL("Natural"), CLEAN_STUDIO("Clean Studio"), SOFT_GLOW("Soft Glow"), CLINICAL_PREMIUM("Clinical Premium"), CINEMATIC("Cinematic"), CUSTOM("Custom") }

/** Beauty/skin parameters. Each value is 0..1 (or -1..1 for bipolar controls). */
@Serializable
data class BeautySettings(
    val enabled: Boolean = false,
    val preset: BeautyPreset = BeautyPreset.NATURAL,
    val intensity: Float = 0.7f,
    val smoothing: Float = 0.45f,
    val toneEven: Float = 0.35f,
    val brightness: Float = 0.1f,
    val exposure: Float = 0f,
    val contrast: Float = 0.04f,
    val saturation: Float = 0.02f,
    val warmth: Float = 0.08f,
    val highlightReduction: Float = 0.25f,
    val shadowLift: Float = 0.15f,
    val clarity: Float = 0.08f,
    val sharpen: Float = 0.2f,
    val underEye: Float = 0.4f,
    val faceRefine: Float = 0f,
    val glow: Float = 0f,
    val split: Float = 0f,
) {
    companion object {
        /**
         * Calibrated presets (v2). Each preset has a distinct character so switching is visible at
         * medium intensity, while smoothing keeps pore detail (see shader) to avoid a plastic look.
         *  Natural         – light smoothing, gentle lift, true-to-life colour.
         *  Clean Studio    – brighter, crisper, neutral-cool; like a softbox in front of you.
         *  Soft Glow       – strongest smoothing, warm, lower contrast, soft bloom.
         *  Clinical Premium– restrained skin, cool, high clarity: credible and sharp.
         *  Cinematic       – contrast, warm highlights / cool shadows split-tone, deeper look.
         */
        fun forPreset(p: BeautyPreset, intensity: Float = 0.7f): BeautySettings = when (p) {
            BeautyPreset.NATURAL -> BeautySettings(true, p, intensity, smoothing = 0.45f, toneEven = 0.35f, brightness = 0.10f, contrast = 0.04f, saturation = 0.02f, warmth = 0.08f, highlightReduction = 0.25f, shadowLift = 0.15f, clarity = 0.08f, sharpen = 0.20f, underEye = 0.40f)
            BeautyPreset.CLEAN_STUDIO -> BeautySettings(true, p, intensity, smoothing = 0.55f, toneEven = 0.45f, brightness = 0.28f, exposure = 0.12f, contrast = 0.16f, saturation = -0.06f, warmth = -0.10f, highlightReduction = 0.35f, shadowLift = 0.30f, clarity = 0.20f, sharpen = 0.35f, underEye = 0.55f)
            BeautyPreset.SOFT_GLOW -> BeautySettings(true, p, intensity, smoothing = 0.80f, toneEven = 0.55f, brightness = 0.22f, exposure = 0.06f, contrast = -0.18f, saturation = 0.08f, warmth = 0.30f, highlightReduction = 0.10f, shadowLift = 0.35f, clarity = 0f, sharpen = 0.05f, underEye = 0.60f, faceRefine = 0.15f, glow = 0.55f)
            BeautyPreset.CLINICAL_PREMIUM -> BeautySettings(true, p, intensity, smoothing = 0.32f, toneEven = 0.30f, brightness = 0.14f, contrast = 0.20f, saturation = -0.14f, warmth = -0.25f, highlightReduction = 0.30f, shadowLift = 0.12f, clarity = 0.35f, sharpen = 0.40f, underEye = 0.35f)
            BeautyPreset.CINEMATIC -> BeautySettings(true, p, intensity, smoothing = 0.45f, toneEven = 0.35f, brightness = -0.06f, contrast = 0.38f, saturation = -0.16f, warmth = 0.22f, highlightReduction = 0.45f, shadowLift = -0.10f, clarity = 0.25f, sharpen = 0.20f, underEye = 0.30f, glow = 0.15f, split = 0.8f)
            BeautyPreset.CUSTOM -> BeautySettings(enabled = true, preset = p, intensity = intensity)
        }
    }
}

@Serializable
enum class ColorLook(val label: String) { NONE("Original"), NATURAL("Natural"), CLEAN("Clean"), WARM("Warm"), COOL_CLINICAL("Cool Clinical"), CINEMATIC("Cinematic"), MOODY("Moody"), BRIGHT_SOCIAL("Bright Social") }

/** Global color grade applied to the whole reel. Values -1..1. */
@Serializable
data class ColorGrade(
    val look: ColorLook = ColorLook.NATURAL,
    val intensity: Float = 0.7f,
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
)

@Serializable
data class FaceSample(
    val tMs: Long,
    val cx: Float, val cy: Float, val w: Float, val h: Float,
    val leftEyeX: Float = -1f, val leftEyeY: Float = -1f,
    val rightEyeX: Float = -1f, val rightEyeY: Float = -1f,
)

/** Face positions over source time, normalized to the display-oriented source frame. */
@Serializable
data class FaceTrack(val samples: List<FaceSample> = emptyList(), val sampledAt: Long = 0)

/** A media assignment. Splits of a source create multiple clips that reference the same source. */
@Serializable
data class MediaClip(
    val id: String,
    val projectId: String,
    val sectionId: String,
    val role: ClipRole = ClipRole.PRIMARY,
    val order: Int = 0,
    val kind: MediaKind = MediaKind.VIDEO,
    val origin: ClipOrigin = ClipOrigin.IMPORT,
    /** Absolute file path (managed copy / recording) or content:// URI with persisted permission. */
    val uri: String,
    val displayName: String = "",
    val sourceDurationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 0f,
    val hasAudio: Boolean = false,
    val videoMime: String? = null,
    val trimStartMs: Long = 0,
    val trimEndMs: Long = 0,
    /** For B-roll: offset within section. Null = automatic placement. */
    val placementMs: Long? = null,
    val transform: ClipTransform = ClipTransform(),
    val volume: Float = 1f,
    val muted: Boolean = false,
    val beauty: BeautySettings = BeautySettings(),
    val isClinical: Boolean = false,
    val identifiablePatient: Boolean = false,
    val consent: ConsentState = ConsentState.NOT_REQUIRED,
    val faceTrack: FaceTrack? = null,
    val thumbnailPath: String? = null,
    val createdAt: Long = 0,
    val missing: Boolean = false,
) {
    val trimmedDurationMs: Long get() = (trimEndMs - trimStartMs).coerceAtLeast(0)
    val isVisual: Boolean get() = kind != MediaKind.AUDIO
    val displayWidth: Int get() = if (rotationDegrees % 180 == 0) width else height
    val displayHeight: Int get() = if (rotationDegrees % 180 == 0) height else width
}

@Serializable
data class SfxCue(val id: String, val timeMs: Long, val sfxId: String, val gain: Float = 0.7f)

@Serializable
data class MusicSettings(
    val trackId: String? = null,     // bundled id or "file:<path>"
    val volume: Float = 0.28f,
    val ducking: Boolean = true,
    val duckDepth: Float = 0.55f,    // fraction of reduction during speech
    val fadeInMs: Long = 800,
    val fadeOutMs: Long = 1600,
    val startOffsetMs: Long = 0,
)

@Serializable
data class AudioMix(
    val dialogueVolume: Float = 1f,
    val sfxVolume: Float = 0.6f,
    val noiseReduction: NoiseReduction = NoiseReduction.STANDARD,
    val speechEq: Boolean = true,
    val compression: Boolean = true,
    val normalize: Boolean = true,
)

@Serializable
data class TransitionSettings(
    val defaultType: TransitionType = TransitionType.CROSSFADE,
    val durationMs: Long = 400,
    /** Per boundary overrides: key = boundary index (0 = between sections 1 & 2, 1 = 2 & 3, 10+ = within-section cuts). */
    val overrides: Map<String, TransitionType> = emptyMap(),
    val withinSection: TransitionType = TransitionType.CUT,
)

@Serializable
data class VisualEffects(
    val pushIn: Boolean = true,
    val vignette: Float = 0.25f,
    val lightOverlay: Boolean = false,
    val introTitle: Boolean = true,
    val sectionTitles: Boolean = true,
    val textEmphasis: Boolean = true,
)

@Serializable
enum class LogoPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, TOP_CENTER }

@Serializable
data class BrandingUse(
    val enabled: Boolean = true,
    val watermark: Boolean = true,
    val watermarkOnlyOnTreatment: Boolean = false,
    val lowerThird: Boolean = true,
    val ctaOverlay: Boolean = true,
    val logoReveal: Boolean = true,
    val ctaDurationMs: Long = 4000,
    val logoPosition: LogoPosition? = null, // null = brand default
    val logoOffsetX: Float = 0f,
    val logoOffsetY: Float = 0f,
)

@Serializable
data class SafeZone(val top: Float = 0.11f, val bottom: Float = 0.20f, val left: Float = 0.06f, val right: Float = 0.16f)

/** Section fill strategy when footage is shorter than the section. */
@Serializable
enum class FillStrategy { NONE, BRAND_CARD }

/** Groups of edit settings. A group the user has changed is never reset by template automation. */
@Serializable
enum class EditGroup { CAPTIONS, MUSIC, TRANSITIONS, COLOR, BRANDING, SFX, EFFECTS, AUDIO }

@Serializable
data class EditSettings(
    val captions: CaptionSettings = CaptionSettings(),
    val music: MusicSettings = MusicSettings(),
    val audio: AudioMix = AudioMix(),
    val transitions: TransitionSettings = TransitionSettings(),
    val color: ColorGrade = ColorGrade(),
    val effects: VisualEffects = VisualEffects(),
    val branding: BrandingUse = BrandingUse(),
    val sfx: List<SfxCue> = emptyList(),
    val safeZone: SafeZone = SafeZone(),
    val quality: ExportQuality = ExportQuality.STANDARD,
    val fill: Map<String, FillStrategy> = emptyMap(),
    val editVersion: Int = 0,
    val userOverrides: Set<EditGroup> = emptySet(),
) {
    /** Groups whose values differ between this and [other] (bookkeeping fields excluded). */
    fun changedGroups(other: EditSettings): Set<EditGroup> = buildSet {
        if (captions != other.captions) add(EditGroup.CAPTIONS)
        if (music != other.music) add(EditGroup.MUSIC)
        if (transitions != other.transitions) add(EditGroup.TRANSITIONS)
        if (color != other.color) add(EditGroup.COLOR)
        if (branding != other.branding) add(EditGroup.BRANDING)
        if (sfx != other.sfx) add(EditGroup.SFX)
        if (effects != other.effects) add(EditGroup.EFFECTS)
        if (audio != other.audio) add(EditGroup.AUDIO)
    }
}

/** One opening line option with the creative technique it uses. */
@Serializable
data class HookOption(val style: String, val text: String)

/** Metadata of an AI-written (template-driven) script. Production settings never live here. */
@Serializable
data class ScriptMeta(
    val topicTitle: String = "",
    val topicKey: String? = null,
    val creativeAngle: String = "",
    val hookOptions: List<HookOption> = emptyList(),
    val selectedHook: Int = 0,
    /** The hook text currently placed at the start of shot 1 (may come from an earlier hook set). */
    val activeHook: String = "",
    val ctaStyle: String = "",
    val medicalReview: List<ClaimFlag> = emptyList(),
    val qualityNotes: List<String> = emptyList(),
    val model: String = "",
    val promptVersion: String = "",
    val generatedAt: Long = 0,
)

@Serializable
data class ExportInfo(
    val path: String? = null,
    val galleryUri: String? = null,
    val srtPath: String? = null,
    val exportedAt: Long = 0,
    val durationMs: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0,
    val editVersion: Int = -1,
    val validation: List<String> = emptyList(),
)

/** Root project aggregate. */
@Serializable
data class Project(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val language: ReelLanguage,
    val treatmentId: String,
    val duration: ReelDuration,
    val style: ReelStyle,
    val concept: ReelConcept = ReelConcept(),
    val feedback: CreativeFeedback? = null,
    val plan: PlanMeta? = null,
    val planApproved: Boolean = false,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val edit: EditSettings = EditSettings(),
    val captions: CaptionTrack? = null,
    val brandSnapshot: BrandProfile? = null,
    val export: ExportInfo? = null,
    val thumbnailPath: String? = null,
    /** Reelify 2.0: LEGACY for projects created before templates existed. */
    val mode: app.reelify.core.template.ProductionMode = app.reelify.core.template.ProductionMode.LEGACY,
    /** Full snapshot of the template version this project was created with. */
    val template: app.reelify.core.template.ReelTemplate? = null,
    val templateOverrides: app.reelify.core.template.TemplateOverrides = app.reelify.core.template.TemplateOverrides(),
    val script: ScriptMeta? = null,
) {
    val isTemplate: Boolean get() = mode == app.reelify.core.template.ProductionMode.TEMPLATE && template != null
}

data class ProjectBundle(
    val project: Project,
    val sections: List<ReelSection>,
    val clips: List<MediaClip>,
) {
    fun section(id: String) = sections.firstOrNull { it.id == id }
    fun clipsFor(sectionId: String, role: ClipRole? = null) =
        clips.filter { it.sectionId == sectionId && (role == null || it.role == role) }.sortedBy { it.order }
    fun sectionStartMs(section: ReelSection) = project.duration.sectionStartMs(section.index - 1)
}
