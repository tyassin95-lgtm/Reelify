package app.reelify.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Language of the reel content (independent of the app's UI language). */
@Serializable
enum class ReelLanguage(
    val code: String,
    val deepgramCode: String,
    val isRtl: Boolean,
    val displayName: String,
    val nativeName: String,
) {
    @SerialName("en")
    ENGLISH("en", "en", false, "English", "English"),

    @SerialName("ar-IQ")
    IRAQI_ARABIC("ar-IQ", "ar-IQ", true, "Arabic (Iraqi)", "عراقي");

    companion object {
        fun fromCode(code: String?): ReelLanguage = entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

/** Supported reel lengths. Every reel has exactly three sections of equal length. */
@Serializable
enum class ReelDuration(val totalMs: Long, val label: String) {
    @SerialName("30") S30(30_000, "30s"),
    @SerialName("45") S45(45_000, "45s"),
    @SerialName("60") S60(60_000, "60s");

    val sectionMs: Long get() = totalMs / SECTION_COUNT
    val seconds: Int get() = (totalMs / 1000).toInt()

    fun sectionStartMs(index: Int): Long = sectionMs * index

    companion object {
        const val SECTION_COUNT = 3
        fun fromMs(ms: Long): ReelDuration = entries.firstOrNull { it.totalMs == ms } ?: S30
    }
}

@Serializable
enum class ReelStyle(val wire: String, val label: String, val blurb: String) {
    @SerialName("educational") EDUCATIONAL("educational", "Educational", "Teach one clear idea"),
    @SerialName("promotional") PROMOTIONAL("promotional", "Promotional", "Showcase a service"),
    @SerialName("myth_vs_fact") MYTH_VS_FACT("myth_vs_fact", "Myth vs Fact", "Bust a misconception"),
    @SerialName("problem_solution") PROBLEM_SOLUTION("problem_solution", "Problem / Solution", "Concern → approach"),
    @SerialName("treatment_demo") TREATMENT_DEMO("treatment_demo", "Treatment Demo", "Show the procedure"),
    @SerialName("talking_to_camera") TALKING_TO_CAMERA("talking_to_camera", "Talking to Camera", "Personal & direct"),
    @SerialName("before_after") BEFORE_AFTER("before_after", "Before & After", "Honest comparison"),
    @SerialName("mixed") MIXED_TALKING_BROLL("mixed", "Talking + B-roll", "Speak, then show");

    companion object {
        fun fromWire(w: String?): ReelStyle = entries.firstOrNull { it.wire == w } ?: EDUCATIONAL
    }
}

@Serializable
enum class SectionPurpose(val wire: String, val label: String) {
    @SerialName("hook") HOOK("hook", "Hook"),
    @SerialName("explanation") EXPLANATION("explanation", "Explanation"),
    @SerialName("demonstration") DEMONSTRATION("demonstration", "Demonstration"),
    @SerialName("myth") MYTH("myth", "Myth"),
    @SerialName("fact") FACT("fact", "Fact"),
    @SerialName("problem") PROBLEM("problem", "Problem"),
    @SerialName("solution") SOLUTION("solution", "Solution"),
    @SerialName("call_to_action") CALL_TO_ACTION("call_to_action", "Call to Action");

    companion object {
        fun fromWire(w: String?): SectionPurpose? = entries.firstOrNull { it.wire == w }
    }
}

@Serializable
enum class ShotType(val wire: String, val label: String, val needsSpeech: Boolean) {
    @SerialName("talking_head") TALKING_HEAD("talking_head", "Talking head", true),
    @SerialName("treatment_broll") TREATMENT_BROLL("treatment_broll", "Treatment B-roll", false),
    @SerialName("voiceover_broll") VOICEOVER_BROLL("voiceover_broll", "Voiceover + B-roll", true),
    @SerialName("device_closeup") DEVICE_CLOSEUP("device_closeup", "Device close-up", false),
    @SerialName("clinic_ambience") CLINIC_AMBIENCE("clinic_ambience", "Clinic ambience", false),
    @SerialName("before_after") BEFORE_AFTER("before_after", "Before / After", false),
    @SerialName("still_image") STILL_IMAGE("still_image", "Still image", false),
    @SerialName("text_card") TEXT_CARD("text_card", "Text card", false);

    /** Footage likely to show clinically representative skin; beauty defaults to OFF. */
    val isClinical: Boolean get() = this == TREATMENT_BROLL || this == BEFORE_AFTER

    companion object {
        fun fromWire(w: String?): ShotType? = entries.firstOrNull { it.wire == w }
    }
}

/** How the user intends to obtain media for a section (production checklist). */
@Serializable
enum class MediaPlan(val wire: String, val label: String) {
    @SerialName("record_talking") RECORD_TALKING("record_talking", "Record talking footage"),
    @SerialName("import_footage") IMPORT_FOOTAGE("import_footage", "Import existing footage"),
    @SerialName("treatment_broll") TREATMENT_BROLL("treatment_broll", "Use treatment B-roll"),
    @SerialName("still_image") STILL_IMAGE("still_image", "Add a still image"),
    @SerialName("voiceover") VOICEOVER("voiceover", "Record voiceover");

    companion object {
        fun fromWire(w: String?): MediaPlan? = entries.firstOrNull { it.wire == w }
        fun defaultFor(shot: ShotType): MediaPlan = when (shot) {
            ShotType.TALKING_HEAD -> RECORD_TALKING
            ShotType.TREATMENT_BROLL, ShotType.DEVICE_CLOSEUP, ShotType.CLINIC_AMBIENCE -> TREATMENT_BROLL
            ShotType.VOICEOVER_BROLL -> VOICEOVER
            ShotType.BEFORE_AFTER, ShotType.STILL_IMAGE, ShotType.TEXT_CARD -> STILL_IMAGE
        }
    }
}

@Serializable
enum class TransitionType(val wire: String, val label: String) {
    @SerialName("cut") CUT("cut", "Clean Cut"),
    @SerialName("crossfade") CROSSFADE("crossfade", "Crossfade"),
    @SerialName("zoom") SUBTLE_ZOOM("zoom", "Subtle Zoom"),
    @SerialName("dissolve") DISSOLVE("dissolve", "Dissolve"),
    @SerialName("slide") SLIDE("slide", "Slide"),
    @SerialName("flash") LIGHT_FLASH("flash", "Light Flash"),
    @SerialName("blur") SOFT_BLUR("blur", "Soft Blur");

    companion object {
        fun fromWire(w: String?): TransitionType? = entries.firstOrNull { it.wire == w }
    }
}

@Serializable
enum class MusicMood(val wire: String, val label: String) {
    @SerialName("elegant") ELEGANT("elegant", "Elegant"),
    @SerialName("cinematic") CINEMATIC("cinematic", "Cinematic"),
    @SerialName("modern") MODERN("modern", "Modern"),
    @SerialName("luxury") LUXURY("luxury", "Luxury"),
    @SerialName("soft_electronic") SOFT_ELECTRONIC("soft_electronic", "Soft Electronic"),
    @SerialName("minimal") MINIMAL("minimal", "Minimal"),
    @SerialName("motivational") MOTIVATIONAL("motivational", "Motivational"),
    @SerialName("calm_clinical") CALM_CLINICAL("calm_clinical", "Calm Clinical"),
    @SerialName("social") SOCIAL("social", "Social Media"),
    @SerialName("ambient") AMBIENT("ambient", "Ambient");

    companion object {
        fun fromWire(w: String?): MusicMood? = entries.firstOrNull { it.wire == w }
    }
}

@Serializable
enum class ProjectStatus(val label: String) {
    DRAFT("Draft"), SCRIPTED("Scripted"), FILMING("Filming"), EDITING("Editing"), EXPORTED("Exported")
}

@Serializable
enum class ClipRole { PRIMARY, BROLL, VOICEOVER }

@Serializable
enum class MediaKind { VIDEO, IMAGE, AUDIO }

@Serializable
enum class ClipOrigin { CAMERA, IMPORT, GENERATED }

@Serializable
enum class ConsentState { NOT_REQUIRED, CONFIRMED, NOT_CONFIRMED }

@Serializable
enum class NoiseReduction(val label: String, val strength: Float) {
    OFF("Off", 0f), LIGHT("Light", 0.35f), STANDARD("Standard", 0.6f), STRONG("Strong", 0.85f)
}

@Serializable
enum class ExportQuality(val label: String, val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    STANDARD("1080p · 30fps", 1080, 1920, 30, 12_000_000),
    HIGH("1080p · 30fps · High bitrate", 1080, 1920, 30, 20_000_000),
    SMOOTH("1080p · 60fps", 1080, 1920, 60, 20_000_000),
}
