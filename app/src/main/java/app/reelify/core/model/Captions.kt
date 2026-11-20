package app.reelify.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CaptionWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1f,
    val emphasized: Boolean = false,
    /** Source anchoring: which dialogue clip and source time this word came from (survives trims). */
    val clipId: String? = null,
    val srcStartMs: Long = -1,
    val srcEndMs: Long = -1,
)

@Serializable
data class CaptionSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<CaptionWord>,
    /** When the user edits text so word count differs, timings are re-distributed. */
    val manuallyEdited: Boolean = false,
) {
    val text: String get() = words.joinToString(" ") { it.text }
}

@Serializable
enum class CaptionSource { DEEPGRAM, SCRIPT_ESTIMATE, MANUAL }

@Serializable
data class CaptionTrack(
    val language: ReelLanguage,
    val segments: List<CaptionSegment>,
    val source: CaptionSource,
    val createdAt: Long,
    val editVersionAtTranscription: Int = 0,
    val dialogueSignature: String = "",
)

@Serializable
enum class CaptionFont(val label: String) { SANS("Modern Sans"), SANS_BOLD("Bold Sans"), ELEGANT("Elegant"), ROUNDED("Rounded") }

@Serializable
enum class CaptionAnimation(val label: String) { NONE("None"), WORD_HIGHLIGHT("Word highlight"), POP("Pop in"), FADE("Fade"), KARAOKE("Karaoke fill") }

@Serializable
enum class CaptionAlign { CENTER, START, END }

@Serializable
enum class CaptionPresetId(val label: String) { MINIMAL_WHITE("Minimal White"), PREMIUM_BURGUNDY("Premium Burgundy"), BOLD_SOCIAL("Bold Social"), CINEMATIC("Cinematic"), CLEAN_CLINICAL("Clean Clinical"), CUSTOM("Custom") }

/** Colors are ARGB packed as Long for serialization stability. */
@Serializable
data class CaptionStyle(
    val preset: CaptionPresetId = CaptionPresetId.PREMIUM_BURGUNDY,
    val font: CaptionFont = CaptionFont.SANS_BOLD,
    val sizeSp: Float = 58f,           // in output pixels at 1080 width
    val textColor: Long = 0xFFFFFFFF,
    val highlightColor: Long = 0xFFE11D48,
    val highlightIsBackground: Boolean = true,
    val backgroundColor: Long = 0x00000000,
    val backgroundEnabled: Boolean = false,
    val strokeColor: Long = 0xCC000000,
    val strokeWidth: Float = 0f,
    val shadow: Boolean = true,
    val positionY: Float = 0.66f,       // vertical center as fraction of height
    val align: CaptionAlign = CaptionAlign.CENTER,
    val animation: CaptionAnimation = CaptionAnimation.WORD_HIGHLIGHT,
    val wordsPerCaption: Int = 4,
    val timingOffsetMs: Long = 0,
    val uppercase: Boolean = false,
    val letterSpacing: Float = 0f,
) {
    companion object {
        fun preset(id: CaptionPresetId): CaptionStyle = when (id) {
            CaptionPresetId.MINIMAL_WHITE -> CaptionStyle(id, CaptionFont.SANS, 54f, 0xFFFFFFFF, 0xFFFFFFFF, false, 0, false, 0x99000000, 0f, true, 0.68f, CaptionAlign.CENTER, CaptionAnimation.FADE, 5)
            CaptionPresetId.PREMIUM_BURGUNDY -> CaptionStyle(id, CaptionFont.SANS_BOLD, 58f, 0xFFFFFFFF, 0xFF9D174D, true, 0, false, 0xCC000000, 0f, true, 0.66f, CaptionAlign.CENTER, CaptionAnimation.WORD_HIGHLIGHT, 4)
            CaptionPresetId.BOLD_SOCIAL -> CaptionStyle(id, CaptionFont.SANS_BOLD, 70f, 0xFFFFFFFF, 0xFFFACC15, false, 0, false, 0xFF000000, 7f, false, 0.64f, CaptionAlign.CENTER, CaptionAnimation.POP, 3, uppercase = true)
            CaptionPresetId.CINEMATIC -> CaptionStyle(id, CaptionFont.ELEGANT, 52f, 0xFFF5F0E8, 0xFFF5D0A9, false, 0, false, 0x99000000, 0f, true, 0.72f, CaptionAlign.CENTER, CaptionAnimation.FADE, 6, letterSpacing = 0.04f)
            CaptionPresetId.CLEAN_CLINICAL -> CaptionStyle(id, CaptionFont.ROUNDED, 52f, 0xFF0F172A, 0xFF0EA5E9, false, 0xF2FFFFFF, true, 0, 0f, false, 0.68f, CaptionAlign.CENTER, CaptionAnimation.WORD_HIGHLIGHT, 5)
            CaptionPresetId.CUSTOM -> CaptionStyle(preset = id)
        }
    }
}

@Serializable
data class CaptionSettings(
    val enabled: Boolean = true,
    val burnIn: Boolean = true,
    val exportSrt: Boolean = false,
    val style: CaptionStyle = CaptionStyle(),
    val highlightKeywords: List<String> = emptyList(),
)
